import { Alert, Button, Input, Select, Switch, Tag } from 'antd';
import { SectionHeader } from '../components/SectionHeader';
import { env } from '../config/env';
import type { RuntimeSetting } from '../types';

const runtimeSettings: RuntimeSetting[] = [
  {
    label: 'API Base URL',
    value: env.apiBaseUrl,
    description: '前端请求后端 REST API 的基础地址，来自 VITE_API_BASE_URL。',
  },
  {
    label: '响应模式',
    value: '普通响应，后续扩展 SSE 流式响应',
    description: 'Agent 问答阶段会切换为可流式输出的响应形态。',
  },
  {
    label: '知识空间',
    value: 'Java 后端学习',
    description: '后续登录后由后端返回用户可访问的知识空间列表。',
  },
];

export function SettingsPage() {
  return (
    <div className="page-stack">
      <SectionHeader
        title="设置"
        description="管理前端运行配置、模型偏好和知识空间选择。当前阶段只展示配置形态，不保存到后端。"
      />

      <Alert
        message="联调准备"
        description="依赖安装与 Vite 预览完成后，可以从这里检查 API 地址、模型配置和知识空间选择是否符合当前开发环境。"
        type="info"
        showIcon
      />

      <div className="two-column">
        <section className="panel">
          <h3>运行配置</h3>
          <div className="compact-list">
            {runtimeSettings.map((setting) => (
              <article key={setting.label}>
                <div className="item-line">
                  <strong>{setting.label}</strong>
                  <Tag>只读</Tag>
                </div>
                <span>{setting.value}</span>
                <p className="muted">{setting.description}</p>
              </article>
            ))}
          </div>
        </section>

        <section className="panel">
          <h3>模型偏好</h3>
          <div className="form-stack">
            <label>
              <span>聊天模型</span>
              <Select
                defaultValue="spring-ai-default"
                options={[
                  { value: 'spring-ai-default', label: 'Spring AI 默认模型' },
                  { value: 'local-ollama', label: '本地 Ollama 模型' },
                  { value: 'cloud-provider', label: '云端模型 Provider' },
                ]}
              />
            </label>
            <label>
              <span>Embedding 模型</span>
              <Input defaultValue="text-embedding-default" />
            </label>
            <label className="switch-row">
              <span>回答必须附带引用来源</span>
              <Switch defaultChecked />
            </label>
            <Button type="primary">保存配置</Button>
          </div>
        </section>
      </div>
    </div>
  );
}
