"""列出所有Flow"""
from app.db.session import session_scope
from app.crud.flow import list_flows


with session_scope() as db:
    flows = list_flows(db, skip=0, limit=100)
    
    if not flows:
        print("❌ 没有找到任何Flow")
    else:
        print(f"📋 找到 {len(flows)} 个Flow:")
        print()
        for flow in flows:
            print(f"   ID: {flow.id} | Name: {flow.name} | CDP: {flow.use_cdp_mode} | Headless: {flow.headless}")
