"""
CDP共享模式执行器
支持连接到用户正在使用的浏览器，实现并发操作
"""

import asyncio
import logging
from typing import Optional, Tuple
from playwright.async_api import Browser, BrowserContext, Page, async_playwright

from .browser_launcher import is_cdp_ready
from .dsl_parser import ParsedStep
from .playwright_executor import PlaywrightExecutor, ExecutionResult

logger = logging.getLogger(__name__)


class SharedBrowserExecutor:
    """
    CDP共享模式执行器
    
    特点：
    - 连接到用户正在使用的浏览器
    - 在新标签页中执行自动化
    - 不干扰用户当前操作
    - 支持并发任务（通过信号量限流）
    """
    
    def __init__(self, max_concurrent_tasks: int = 3):
        """
        Args:
            max_concurrent_tasks: 最大并发任务数，防止资源竞争
        """
        self._semaphore = asyncio.Semaphore(max_concurrent_tasks)
        self._active_connections = 0
        
    async def connect_to_user_browser(
        self,
        port: int = 9222,
        timeout: int = 60000
    ) -> Tuple[Browser, BrowserContext]:
        """
        连接到用户正在使用的浏览器
        
        Args:
            port: CDP调试端口
            timeout: 连接超时时间（毫秒）
            
        Returns:
            (browser, context): 浏览器实例和用户上下文
            
        Raises:
            RuntimeError: CDP未就绪或连接失败
        """
        # 验证CDP是否可用
        if not is_cdp_ready(port):
            raise RuntimeError(
                f"无法连接到CDP端口 {port}\n\n"
                f"请确保浏览器已使用以下参数启动:\n"
                f"chrome.exe --remote-debugging-port={port}\n\n"
                f"或使用快捷方式启动浏览器并开启远程调试"
            )
        
        logger.info(f"CDP端口 {port} 已就绪，正在连接...")
        
        # 连接到CDP
        async with async_playwright() as p:
            try:
                browser = await p.chromium.connect_over_cdp(
                    endpoint_url=f"http://localhost:{port}",
                    timeout=timeout
                )
                
                # 获取用户的默认上下文
                if not browser.contexts:
                    raise RuntimeError(
                        "浏览器没有活动的上下文\n"
                        "请确保浏览器已完全启动并至少打开一个窗口"
                    )
                
                context = browser.contexts[0]
                self._active_connections += 1
                
                logger.info(
                    f"✅ 成功连接到用户浏览器 "
                    f"(当前活动连接: {self._active_connections})"
                )
                
                return browser, context
                
            except Exception as e:
                logger.error(f"CDP连接失败: {e}")
                raise RuntimeError(f"连接到CDP失败: {e}")
    
    async def execute_in_new_page(
        self,
        context: BrowserContext,
        steps: list[ParsedStep],
        executor: PlaywrightExecutor
    ) -> ExecutionResult:
        """
        在新标签页中执行自动化步骤
        
        Args:
            context: 浏览器上下文
            steps: 要执行的步骤列表
            executor: Playwright执行器实例
            
        Returns:
            ExecutionResult: 执行结果
        """
        page = None
        
        try:
            # 创建新标签页
            page = await context.new_page()
            logger.info("📄 创建新标签页用于自动化执行")
            
            # 执行步骤（复用PlaywrightExecutor的逻辑）
            result = await executor._execute_steps_on_page(page, steps)
            
            return result
            
        except Exception as e:
            logger.error(f"自动化执行失败: {e}")
            raise
            
        finally:
            # 清理：关闭自动化创建的标签页
            if page and not page.is_closed():
                try:
                    await page.close()
                    logger.info("✅ 自动化标签页已关闭")
                except:
                    pass  # 可能已被用户关闭
    
    async def execute_with_concurrency_control(
        self,
        flow_id: int,
        steps: list[ParsedStep],
        cdp_port: int,
        executor: PlaywrightExecutor
    ) -> ExecutionResult:
        """
        带并发控制的执行（使用信号量限流）
        
        Args:
            flow_id: 流程ID
            steps: 执行步骤
            cdp_port: CDP端口
            executor: 执行器实例
            
        Returns:
            ExecutionResult: 执行结果
        """
        async with self._semaphore:  # 限制并发数
            logger.info(f"开始执行流程 {flow_id} (共享模式)")
            
            browser = None
            
            try:
                # 连接到用户浏览器
                browser, context = await self.connect_to_user_browser(cdp_port)
                
                # 在新标签页中执行
                result = await self.execute_in_new_page(
                    context, steps, executor
                )
                
                return result
                
            finally:
                # 断开连接（不关闭浏览器）
                if browser:
                    try:
                        await browser.close()
                        self._active_connections -= 1
                        logger.info(
                            f"✅ CDP连接已断开，浏览器继续运行 "
                            f"(剩余活动连接: {self._active_connections})"
                        )
                    except:
                        pass
    
    async def check_page_activity(self, page: Page) -> bool:
        """
        检查页面是否被用户主动使用
        
        Args:
            page: 页面对象
            
        Returns:
            bool: True表示页面在前台活跃
        """
        try:
            is_visible = await page.evaluate(
                "() => document.visibilityState === 'visible'"
            )
            return is_visible
        except:
            return False
    
    async def find_idle_page(self, context: BrowserContext) -> Optional[Page]:
        """
        查找空闲的页面（用户未主动使用）
        
        Args:
            context: 浏览器上下文
            
        Returns:
            Optional[Page]: 空闲页面，或None
        """
        for page in context.pages:
            if not await self.check_page_activity(page):
                logger.info(f"找到空闲页面: {page.url}")
                return page
        
        logger.info("没有空闲页面，将创建新标签页")
        return None


# 全局共享执行器实例
_shared_executor: Optional[SharedBrowserExecutor] = None


def get_shared_executor(max_concurrent: int = 3) -> SharedBrowserExecutor:
    """获取全局共享执行器实例（单例模式）"""
    global _shared_executor
    if _shared_executor is None:
        _shared_executor = SharedBrowserExecutor(max_concurrent)
    return _shared_executor
