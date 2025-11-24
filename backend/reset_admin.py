from sqlmodel import Session, select
from app.db.session import engine
from app.models.user import User
from app.core.security import get_password_hash

# 查看现有用户
with Session(engine) as db:
    users = db.exec(select(User)).all()
    print(f"现有用户数量: {len(users)}")
    for u in users:
        print(f"  - Email: {u.email}, Superuser: {u.is_superuser}")
    
    # 删除所有用户
    if users:
        print("\n删除所有用户...")
        for u in users:
            db.delete(u)
        db.commit()
    
    # 创建新管理员
    print("\n创建新管理员账户...")
    admin = User(
        email="admin@example.com",
        hashed_password=get_password_hash("admin123"),
        full_name="Admin User",
        is_superuser=True
    )
    db.add(admin)
    db.commit()
    db.refresh(admin)
    print(f"✓ 管理员创建成功: {admin.email}")
    print(f"  密码: admin123")
