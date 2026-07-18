import { Alert, Button, Input, InputNumber, Select, Tag, message } from 'antd';
import { RefreshCw, Save } from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { ApiClientError, apiClient } from '../api/client';
import type { CitationPolicy, UserWorkspacePreferenceDto } from '../api/contracts';
import { PageState } from '../components/PageState';
import { SectionHeader } from '../components/SectionHeader';
import { env } from '../config/env';
import { useAppSession } from '../contexts/AppSessionContext';
import type { RuntimeSetting } from '../types';

interface PreferenceDraft {
  chatModel: string;
  embeddingModel: string;
  citationPolicy: CitationPolicy;
  defaultTopK: number;
}

function toDraft(preference: UserWorkspacePreferenceDto): PreferenceDraft {
  return {
    chatModel: preference.chatModel,
    embeddingModel: preference.embeddingModel,
    citationPolicy: preference.citationPolicy,
    defaultTopK: preference.defaultTopK,
  };
}

export function SettingsPage() {
  const { workspaceId = 0 } = useAppSession();
  const [preference, setPreference] = useState<UserWorkspacePreferenceDto>();
  const [draft, setDraft] = useState<PreferenceDraft>();
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string>();

  const preferencePath = `/v1/workspaces/${workspaceId}/preferences`;
  const runtimeSettings: RuntimeSetting[] = [
    {
      label: 'API 地址',
      value: env.apiBaseUrl,
      description: '前端请求后端接口的基础地址，由构建环境变量提供。',
    },
    {
      label: '知识空间编号',
      value: String(workspaceId),
      description: '来自当前用户可访问的知识空间，切换后所有业务页面同步刷新。',
    },
    {
      label: '敏感配置',
      value: '仅后端环境变量或密钥管理服务',
      description: 'API Key、访问令牌和供应商端点不会通过设置接口读取或保存。',
    },
  ];

  const loadPreference = useCallback(async () => {
    setLoading(true);
    setError(undefined);
    try {
      const result = await apiClient.get<UserWorkspacePreferenceDto>(preferencePath);
      setPreference(result);
      setDraft(toDraft(result));
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : '偏好设置加载失败');
    } finally {
      setLoading(false);
    }
  }, [preferencePath]);

  useEffect(() => {
    void loadPreference();
  }, [loadPreference]);

  const changed = useMemo(() => {
    if (!preference || !draft) return false;
    return preference.chatModel !== draft.chatModel.trim()
      || preference.embeddingModel !== draft.embeddingModel.trim()
      || preference.citationPolicy !== draft.citationPolicy
      || preference.defaultTopK !== draft.defaultTopK;
  }, [draft, preference]);

  const savePreference = async () => {
    if (!preference || !draft || !draft.chatModel.trim() || !draft.embeddingModel.trim()) {
      message.warning('请填写聊天模型和 Embedding 模型标识');
      return;
    }

    setSaving(true);
    try {
      const saved = await apiClient.put<UserWorkspacePreferenceDto>(preferencePath, {
        ...draft,
        chatModel: draft.chatModel.trim(),
        embeddingModel: draft.embeddingModel.trim(),
        expectedVersion: preference.version,
      });
      setPreference(saved);
      setDraft(toDraft(saved));
      message.success('偏好设置已保存');
    } catch (saveError) {
      if (saveError instanceof ApiClientError && saveError.status === 409) {
        message.warning('设置已在其他页面更新，已重新加载最新值');
        await loadPreference();
      } else {
        message.error(saveError instanceof Error ? saveError.message : '偏好设置保存失败');
      }
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="page-stack">
      <SectionHeader
        title="设置"
        description="管理当前知识空间的模型标识、检索数量和引用展示偏好。"
        action={<Button icon={<RefreshCw size={16} />} onClick={loadPreference}>刷新</Button>}
      />

      <Alert
        message="配置边界"
        description="这里保存的是非敏感使用偏好。模型服务密钥、接口端点和生产供应商配置仍由后端安全配置管理。"
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

        <PageState loading={loading} error={error} onRetry={loadPreference}>
          <section className="panel">
            <div className="item-line">
              <h3>知识空间偏好</h3>
              <Tag color={preference?.persisted ? 'success' : 'default'}>
                {preference?.persisted ? `已保存 · 版本 ${preference.version}` : '使用系统默认值'}
              </Tag>
            </div>
            <div className="form-stack">
              <label>
                <span>聊天模型标识</span>
                <Input
                  maxLength={120}
                  value={draft?.chatModel}
                  placeholder="例如 gpt-4o-mini"
                  onChange={(event) => setDraft((current) => current
                    ? { ...current, chatModel: event.target.value }
                    : current)}
                />
              </label>
              <label>
                <span>Embedding 模型标识</span>
                <Input
                  maxLength={120}
                  value={draft?.embeddingModel}
                  placeholder="例如 text-embedding-3-small"
                  onChange={(event) => setDraft((current) => current
                    ? { ...current, embeddingModel: event.target.value }
                    : current)}
                />
              </label>
              <label>
                <span>引用策略</span>
                <Select<CitationPolicy>
                  value={draft?.citationPolicy}
                  options={[
                    { value: 'REQUIRED', label: '回答必须展示可用引用' },
                    { value: 'WHEN_AVAILABLE', label: '存在可靠来源时展示引用' },
                  ]}
                  onChange={(citationPolicy) => setDraft((current) => current
                    ? { ...current, citationPolicy }
                    : current)}
                />
              </label>
              <label>
                <span>默认召回数量</span>
                <InputNumber
                  min={1}
                  max={20}
                  value={draft?.defaultTopK}
                  onChange={(defaultTopK) => setDraft((current) => current
                    ? { ...current, defaultTopK: defaultTopK ?? 5 }
                    : current)}
                />
              </label>
              <Button
                type="primary"
                icon={<Save size={16} />}
                loading={saving}
                disabled={!changed}
                onClick={savePreference}
              >
                保存偏好
              </Button>
            </div>
          </section>
        </PageState>
      </div>
    </div>
  );
}
