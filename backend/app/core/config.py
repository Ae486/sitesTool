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

    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8")


settings = Settings()
settings.data_dir.mkdir(parents=True, exist_ok=True)
