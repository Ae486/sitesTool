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
];
