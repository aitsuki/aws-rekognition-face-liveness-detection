use std::{fmt::Debug, sync::Arc};

use aws_config::BehaviorVersion;
use aws_smithy_runtime_api::client::result::SdkError;
use aws_smithy_types_convert::date_time::DateTimeExt;
use axum::{
    extract::{Path, State},
    http::StatusCode,
    response::IntoResponse,
    routing::get,
    Json, Router,
};
use chrono::{DateTime, Utc};

use base64::prelude::*;
use serde::Serialize;
use tokio::net::TcpListener;

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
    let sdk_config = aws_config::load_defaults(BehaviorVersion::latest()).await;
    let app_state = AppState {
        aws_region: sdk_config.region().unwrap().to_string(),
        sts_client: aws_sdk_sts::Client::new(&sdk_config),
        reko_client: aws_sdk_rekognition::Client::new(&sdk_config),
    };

    let app = Router::new()
        .route("/credential", get(get_credentials))
        .route("/liveness/session", get(create_liveness_session))
        .route("/liveness/session/:id", get(get_liveness_sesssion_result))
        .with_state(App::new(app_state));
    let listener = TcpListener::bind("0.0.0.0:8080").await.unwrap();
    axum::serve(listener, app).await.unwrap()
}

/// 获取sts临时授权
async fn get_credentials(State(app): State<App>) -> Result<Json<Credentials>, AppError> {
    let token_result = app.sts_client.get_session_token().send().await?;
    let c = token_result.credentials.unwrap();
    Ok(Json(Credentials {
        access_key_id: c.access_key_id,
        secret_access_key: c.secret_access_key,
        expirtion: c.expiration.to_chrono_utc().unwrap(),
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
    expirtion: DateTime<Utc>,
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
    E: Debug,
{
    fn from(sdk_err: SdkError<E, aws_smithy_runtime_api::http::Response>) -> Self {
        let code = if let Some(resp) = sdk_err.raw_response() {
            println!("aws sdk error body = {:?}", resp.body());
            StatusCode::from_u16(resp.status().as_u16()).unwrap()
        } else {
            StatusCode::INTERNAL_SERVER_ERROR
        };
        AppError::SdkError(code, sdk_err.to_string())
    }
}

impl IntoResponse for AppError {
    fn into_response(self) -> axum::response::Response {
        match self {
            AppError::SdkError(code, msg) => (code, msg).into_response(),
        }
    }
}
