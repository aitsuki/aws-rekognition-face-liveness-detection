use std::sync::Arc;

use aws_config::{meta::region::RegionProviderChain, BehaviorVersion};
use aws_sdk_sts::error::ProvideErrorMetadata;
use aws_smithy_runtime_api::client::result::SdkError;
use aws_smithy_types_convert::date_time::DateTimeExt;
use axum::{
    extract::{MatchedPath, Path, State},
    http::{Request, StatusCode},
    response::IntoResponse,
    routing::get,
    Json, Router,
};
use chrono::{DateTime, Utc};

use base64::prelude::*;
use clap::Parser;
use serde::Serialize;
use serde_json::json;
use tokio::net::TcpListener;
use tower_http::trace::TraceLayer;
use tracing::info_span;
use tracing_subscriber::{layer::SubscriberExt, util::SubscriberInitExt};

#[derive(clap::Parser)]
struct Config {
    #[arg(short, long)]
    port: Option<u16>,
}

struct AppState {
    aws_region: String,
    sts_client: aws_sdk_sts::Client,
    reko_client: aws_sdk_rekognition::Client,
}

/// AWS rekognition face liveness 流程：
/// 1. app 请求 credentials 用于 FaceLivenessDetector 控件的鉴权
/// （PS：除了服务器提供临时授权以外，也可以通过前段sdk提供，类似firebase的google-service.json）
/// 2. app 请求一个 session 开始进行活体检测流程
/// 3. 通过 session 查询活体检测结果
#[tokio::main]
async fn main() {
    let config = Config::parse();
    let port = match config.port {
        Some(p) => p,
        None => 8080,
    };

    tracing_subscriber::registry()
        .with(
            tracing_subscriber::EnvFilter::try_from_default_env().unwrap_or_else(|_| {
                "aws_face_liveness=trace,tower_http=debug,axum::rejection=trace".into()
            }),
        )
        .with(tracing_subscriber::fmt::layer())
        .init();

    let region_provider = RegionProviderChain::default_provider().or_else("us-east-1");
    let sdk_config = aws_config::defaults(BehaviorVersion::latest())
        .region(region_provider)
        .load()
        .await;

    let app_state = AppState {
        aws_region: sdk_config.region().unwrap().to_string(),
        sts_client: aws_sdk_sts::Client::new(&sdk_config),
        reko_client: aws_sdk_rekognition::Client::new(&sdk_config),
    };

    let app = Router::new()
        .route("/health", get(check_health))
        .route("/credentials", get(get_credentials))
        .route("/liveness/session", get(create_liveness_session))
        .route("/liveness/session/:id", get(get_liveness_sesssion_result))
        .layer(
            TraceLayer::new_for_http().make_span_with(|request: &Request<_>| {
                let matched_path = request
                    .extensions()
                    .get::<MatchedPath>()
                    .map(MatchedPath::as_str);

                info_span!(
                    "http",
                    method = ?request.method(),
                    matched_path,
                    some_other_field = tracing::field::Empty,
                )
            }),
        )
        .with_state(App::new(app_state));

    let listener = TcpListener::bind(format!("0.0.0.0:{}", port))
        .await
        .unwrap();
    tracing::debug!("listening on {}", listener.local_addr().unwrap());
    axum::serve(listener, app).await.unwrap()
}

async fn check_health() -> &'static str {
    "ok"
}

/// 获取sts临时授权
async fn get_credentials(State(app): State<App>) -> Result<Json<Credentials>, AppError> {
    let token_result = app.sts_client.get_session_token().send().await?;
    let c = token_result.credentials.unwrap();
    Ok(Json(Credentials {
        access_key_id: c.access_key_id,
        secret_access_key: c.secret_access_key,
        expiration: c.expiration.to_chrono_utc().unwrap(),
        session_token: c.session_token,
    }))
}

/// 创建 face liveness session 以开始活体检测，
/// session单次有效，重新检测需要分配新的session
async fn create_liveness_session(
    State(app): State<App>,
) -> Result<Json<LivenessSession>, AppError> {
    let session_result = app
        .reko_client
        .create_face_liveness_session()
        .send()
        .await?;
    let session_id = session_result.session_id;
    Ok(Json(LivenessSession {
        session_id,
        region: app.aws_region.clone(),
    }))
}

/// 根据sessionId获取活体检测结果，成功获取后该session立即失效，
/// 否则session过期后自动失效（创建session时可配置，最大30分钟）
async fn get_liveness_sesssion_result(
    Path(id): Path<String>,
    State(app): State<App>,
) -> Result<Json<LivenessResult>, AppError> {
    let results = app
        .reko_client
        .get_face_liveness_session_results()
        .session_id(id)
        .send()
        .await?;

    let ref_img = results
        .reference_image
        .and_then(|image| image.bytes)
        .map(|blob| {
            let bytes = blob.as_ref();
            BASE64_STANDARD.encode(bytes)
        });

    Ok(Json(LivenessResult {
        status: results.status.to_string(),
        confidence: results.confidence,
        reference_image: ref_img,
    }))
}

#[derive(Serialize)]
struct Credentials {
    access_key_id: String,
    secret_access_key: String,
    expiration: DateTime<Utc>,
    session_token: String,
}

#[derive(Serialize)]
struct LivenessSession {
    region: String,
    session_id: String,
}

#[derive(Serialize)]
struct LivenessResult {
    status: String,
    confidence: Option<f32>,
    reference_image: Option<String>,
}

type App = Arc<AppState>;

enum AppError {
    SdkError(StatusCode, String),
}

impl<E> From<SdkError<E, aws_smithy_runtime_api::http::Response>> for AppError
where
    E: ProvideErrorMetadata,
{
    fn from(sdk_err: SdkError<E, aws_smithy_runtime_api::http::Response>) -> Self {
        let code = if let Some(resp) = sdk_err.raw_response() {
            StatusCode::from_u16(resp.status().as_u16()).unwrap()
        } else {
            StatusCode::INTERNAL_SERVER_ERROR
        };
        let message = sdk_err
            .message()
            .map(|m| m.to_string())
            .unwrap_or(sdk_err.to_string());
        tracing::error!(
            "AWS sdk error: code = {}, meta = {:?}",
            code,
            sdk_err.meta()
        );
        AppError::SdkError(code, message)
    }
}

impl IntoResponse for AppError {
    fn into_response(self) -> axum::response::Response {
        let (code, message) = match self {
            AppError::SdkError(code, msg) => (code, msg),
        };
        (code, Json(json!({"error": message}))).into_response()
    }
}
