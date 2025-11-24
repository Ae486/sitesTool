from fastapi import APIRouter

from app.api.routes import auth, catalog, flows, history, sites

api_router = APIRouter()

api_router.include_router(auth.router, prefix="/auth", tags=["auth"])
api_router.include_router(catalog.router, prefix="/catalog", tags=["catalog"])
api_router.include_router(sites.router, prefix="/sites", tags=["sites"])
api_router.include_router(flows.router, prefix="/flows", tags=["automation"])
api_router.include_router(history.router, prefix="/history", tags=["history"])
