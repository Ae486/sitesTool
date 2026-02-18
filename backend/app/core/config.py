from pathlib import Path

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    project_name: str = "Navigator Automation Hub"
    version: str = "0.1.0"
    api_prefix: str = "/api"
    secret_key: str = Field("change-me", min_length=8)
    access_token_expire_minutes: int = 60
    database_url: str = "sqlite:///./data/app.db"
    environment: str = "local"
    scheduler_timezone: str = "Asia/Shanghai"
    data_dir: Path = Path("./data")
    disable_auth: bool = False  # 设置为 True 可禁用认证（仅开发环境）

    # CORS 配置：逗号分隔的允许域名列表
    # 本地开发默认允许常用端口，生产环境请设置具体域名
    cors_origins: str = "http://localhost:5173,http://localhost:3000,http://127.0.0.1:5173"

    automation_max_running_flows: int = 0
    automation_process_timeout_seconds: int = 300

    @property
    def cors_origin_list(self) -> list[str]:
        """解析 CORS 允许域名列表"""
        if self.environment == "local":
            # 本地开发环境：允许所有来源（方便调试）
            return ["*"]
        # 生产环境：只允许配置的域名
        return [origin.strip() for origin in self.cors_origins.split(",") if origin.strip()]

    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8")


settings = Settings()
settings.data_dir.mkdir(parents=True, exist_ok=True)
