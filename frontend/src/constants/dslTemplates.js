export const DSL_TEMPLATES = [
    {
        value: "login_checkin",
        label: "登录并签到",
        description: "示例：打开登录页，输入账号密码，点击签到按钮。",
        dsl: {
            version: 1,
            steps: [
                { type: "navigate", url: "http://127.0.0.1:8000/test/" },
                {
                    type: "input",
                    selector: "#username",
                    value: "admin",
                    description: "输入用户名",
                },
                {
                    type: "input",
                    selector: "#password",
                    value: "123456",
                    description: "输入密码",
                },
                { type: "click", selector: "#login-btn", description: "点击登录" },
                {
                    type: "wait_for",
                    selector: "#checkin-btn",
                    description: "等待签到按钮出现",
                },
                { type: "click", selector: "#checkin-btn", description: "点击签到" },
                { type: "screenshot", description: "完成后截图" },
            ],
        },
    },
    {
        value: "extract_points",
        label: "数据提取",
        description: "示例：提取积分文本并滚动查看历史记录。",
        dsl: {
            version: 1,
            steps: [
                { type: "navigate", url: "http://127.0.0.1:8000/test/" },
                {
                    type: "wait_for",
                    selector: "#reward-points",
                    description: "等待积分展示区域",
                },
                {
                    type: "extract",
                    selector: "#reward-points .points",
                    variable: "current_points",
                    description: "提取积分数值",
                },
                {
                    type: "scroll",
                    selector: "#history-table",
                    description: "滚动到历史表格",
                },
                { type: "screenshot", description: "保存历史记录截图" },
            ],
        },
    },
    {
        value: "checkbox_select",
        label: "选项切换",
        description: "示例：切换复选框、选择下拉项并等待提示。",
        dsl: {
            version: 1,
            steps: [
                { type: "navigate", url: "http://127.0.0.1:8000/test/" },
                {
                    type: "checkbox",
                    selector: "#agree-terms",
                    checked: true,
                    description: "勾选协议复选框",
                },
                {
                    type: "select",
                    selector: "#city-select",
                    value: "shanghai",
                    description: "选择城市",
                },
                {
                    type: "wait_time",
                    duration: 2000,
                    description: "等待 2 秒确认效果",
                },
                { type: "screenshot", description: "截取状态图片" },
            ],
        },
    },
    {
        value: "lottery_redeem",
        label: "抽奖获取兑换码",
        description: "高级：抽奖获取兑换码，判断是否中奖，提取并使用兑换码",
        dsl: {
            version: 1,
            steps: [
                { type: "navigate", url: "http://127.0.0.1:5173/dsl-test.html", description: "打开测试页面" },
                { type: "click", selector: "#btn-lottery", description: "点击抽奖" },
                { type: "random_delay", min: 500, max: 1500, description: "模拟人类等待" },
                { type: "if_exists", selector: "#won-code", variable: "won", timeout: 2000, description: "检查是否中奖" },
                { type: "try_click", selector: "#lottery-code strong", description: "尝试点击兑换码（如果存在）" },
                { type: "screenshot", description: "记录抽奖结果" },
            ],
        },
    },
    {
        value: "batch_extract_codes",
        label: "批量提取兑换码",
        description: "高级：批量提取所有兑换码到数组变量",
        dsl: {
            version: 1,
            steps: [
                { type: "navigate", url: "http://127.0.0.1:5173/dsl-test.html", description: "打开测试页面" },
                { type: "wait_for", selector: ".code-list", description: "等待列表加载" },
                { type: "extract_all", selector: ".code-text", variable: "all_codes", description: "批量提取所有兑换码" },
                { type: "eval_js", script: "return document.querySelectorAll('.code-text').length", variable: "code_count", description: "获取兑换码数量" },
                { type: "screenshot", description: "截取兑换码列表" },
            ],
        },
    },
    {
        value: "keyboard_hover_test",
        label: "键盘和悬停测试",
        description: "高级：测试键盘输入、悬停菜单等交互",
        dsl: {
            version: 1,
            steps: [
                { type: "navigate", url: "http://127.0.0.1:5173/dsl-test.html", description: "打开测试页面" },
                { type: "input", selector: "#keyboard-input", value: "Hello DSL!", description: "输入文本" },
                { type: "keyboard", key: "Enter", description: "按回车提交" },
                { type: "hover", selector: "#hover-menu button", description: "悬停显示菜单" },
                { type: "wait_time", duration: 500, description: "等待菜单显示" },
                { type: "click", selector: "#dropdown-menu a:first-child", description: "点击菜单项" },
                { type: "screenshot", description: "截取结果" },
            ],
        },
    },
    {
        value: "variable_assertion_test",
        label: "变量与断言测试",
        description: "高级：设置变量、提取数据、断言验证",
        dsl: {
            version: 1,
            steps: [
                { type: "navigate", url: "http://127.0.0.1:5173/dsl-test.html", description: "打开测试页面" },
                { type: "set_variable", variable: "test_code", value: "ABC123", description: "设置测试兑换码" },
                { type: "input", selector: "#redeem-input", value: "{{test_code}}", description: "输入变量值" },
                { type: "click", selector: "#btn-redeem", description: "点击兑换" },
                { type: "wait_time", duration: 500, description: "等待结果" },
                { type: "assert_text", selector: "#redeem-result", expected: "兑换成功", description: "断言兑换成功" },
                { type: "extract", selector: "#redeem-result", variable: "result_text", description: "提取结果文本" },
                { type: "screenshot", description: "截取最终结果" },
            ],
        },
    },
    {
        value: "loop_click_counter",
        label: "循环点击计数器",
        description: "高级：循环执行点击操作",
        dsl: {
            version: 1,
            steps: [
                { type: "navigate", url: "http://127.0.0.1:5173/dsl-test.html", description: "打开测试页面" },
                { type: "scroll", selector: "#counter", description: "滚动到计数器" },
                { type: "loop", times: 5, steps_count: 2, description: "循环5次" },
                { type: "click", selector: "#btn-increment", description: "点击+1" },
                { type: "random_delay", min: 200, max: 500, description: "随机延迟" },
                { type: "extract", selector: "#counter", variable: "final_count", description: "提取最终值" },
                { type: "assert_text", selector: "#counter", expected: "5", description: "验证计数为5" },
            ],
        },
    },
];
