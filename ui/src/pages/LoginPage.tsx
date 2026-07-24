import { Button, Form, Input, Modal, Tabs, message } from 'antd';
import {
  ArrowRight,
  BookOpenText,
  BrainCircuit,
  KeyRound,
  LibraryBig,
  LockKeyhole,
  RefreshCw,
  ShieldCheck,
  Sparkles,
  UserPlus,
} from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { apiClient } from '../api/client';
import type { CaptchaChallengeDto } from '../api/contracts';
import { env } from '../config/env';
import { useAppSession } from '../contexts/AppSessionContext';

interface CredentialValues {
  username: string;
  password: string;
  captchaCode: string;
}

interface PasswordValues {
  username: string;
  currentPassword: string;
  newPassword: string;
  confirmPassword: string;
  captchaCode: string;
}

/** 本地 JWT 与 OIDC 模式共用的身份入口。 */
export function LoginPage() {
  const { login, loginWithOidc, register } = useAppSession();
  const [submitting, setSubmitting] = useState(false);
  const [captcha, setCaptcha] = useState<CaptchaChallengeDto>();
  const [passwordCaptcha, setPasswordCaptcha] = useState<CaptchaChallengeDto>();
  const [passwordOpen, setPasswordOpen] = useState(false);
  const [passwordForm] = Form.useForm<PasswordValues>();

  const loadCaptcha = useCallback(async (target: 'login' | 'password' = 'login') => {
    try {
      const challenge = await apiClient.get<CaptchaChallengeDto>('/v1/auth/captcha');
      if (target === 'login') setCaptcha(challenge);
      else setPasswordCaptcha(challenge);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '验证码加载失败');
    }
  }, []);

  useEffect(() => {
    if (env.authMode === 'local-jwt') void loadCaptcha();
  }, [loadCaptcha]);

  const run = async (action: () => Promise<void>, refreshTarget?: 'login' | 'password') => {
    setSubmitting(true);
    try {
      await action();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '认证失败');
      if (refreshTarget) void loadCaptcha(refreshTarget);
    } finally {
      setSubmitting(false);
    }
  };

  const submitLogin = (values: CredentialValues) => run(() => login({
    username: values.username,
    password: values.password,
    captchaChallengeId: captcha?.challengeId ?? '',
    captchaCode: values.captchaCode,
  }), 'login');

  const openPasswordModal = () => {
    passwordForm.resetFields();
    setPasswordOpen(true);
    void loadCaptcha('password');
  };

  const changePassword = (values: PasswordValues) => run(async () => {
    await apiClient.post<void>('/v1/auth/password', {
      username: values.username,
      currentPassword: values.currentPassword,
      newPassword: values.newPassword,
      captchaChallengeId: passwordCaptcha?.challengeId ?? '',
      captchaCode: values.captchaCode,
    });
    message.success('密码修改成功，请使用新密码登录');
    setPasswordOpen(false);
    passwordForm.resetFields();
    await loadCaptcha();
  }, 'password');

  return (
    <main className="auth-page">
      <section className="auth-story">
        <header className="auth-story__brand">
          <span><BookOpenText size={22} /></span>
          <strong>AgentMind</strong>
        </header>
        <div className="auth-story__content">
          <span className="auth-story__eyebrow"><Sparkles size={14} /> PERSONAL KNOWLEDGE OS</span>
          <h1>让每一份资料，成为可检索、可理解、可复习的长期知识。</h1>
          <p>从采集到问答，再到复习计划，AgentMind 将个人学习资料整理为持续增长的知识系统。</p>
          <div className="auth-capability-grid">
            <article><LibraryBig size={19} /><div><strong>多源知识采集</strong><span>文件、网页与结构化知识沉淀</span></div></article>
            <article><BrainCircuit size={19} /><div><strong>可溯源 Agent</strong><span>基于私有资料回答并返回出处</span></div></article>
            <article><ShieldCheck size={19} /><div><strong>空间级隔离</strong><span>账号、知识空间与权限完整隔离</span></div></article>
          </div>
        </div>
        <footer>Java 21 · Spring AI · RAG · Tool Calling</footer>
      </section>

      <section className="auth-entry">
        <div className="auth-entry__inner">
          <div className="auth-entry__heading">
            <span className="auth-entry__icon"><LockKeyhole size={20} /></span>
            <div><h2>欢迎回来</h2><p>登录后继续管理你的知识与学习进度</p></div>
          </div>
          {env.authMode === 'oidc' ? (
            <Button
              block
              type="primary"
              size="large"
              loading={submitting}
              icon={<ArrowRight size={17} />}
              onClick={() => run(loginWithOidc)}
            >
              使用身份提供方登录
            </Button>
          ) : (
            <Tabs
              className="auth-tabs"
              items={[
                {
                  key: 'login',
                  label: '账号登录',
                  children: (
                    <Form layout="vertical" requiredMark={false} onFinish={submitLogin}>
                      <Form.Item label="用户名" name="username" rules={[{ required: true, message: '请输入用户名' }]}>
                        <Input size="large" autoComplete="username" placeholder="输入用户名" />
                      </Form.Item>
                      <Form.Item label="密码" name="password" rules={[{ required: true, message: '请输入密码' }]}>
                        <Input.Password size="large" autoComplete="current-password" placeholder="输入登录密码" />
                      </Form.Item>
                      <Form.Item label="安全验证" required>
                        <div className="captcha-row">
                          <Form.Item noStyle name="captchaCode" rules={[{ required: true, message: '请输入验证码' }]}>
                            <Input size="large" maxLength={4} placeholder="验证码" autoComplete="off" />
                          </Form.Item>
                          <button
                            className="captcha-image"
                            type="button"
                            title="点击刷新验证码"
                            onClick={() => void loadCaptcha()}
                          >
                            {captcha ? <img src={captcha.imageDataUri} alt="登录验证码" /> : <RefreshCw size={17} />}
                          </button>
                        </div>
                      </Form.Item>
                      <div className="auth-form-meta">
                        <span><ShieldCheck size={14} /> 验证码 3 分钟内有效</span>
                        <button type="button" onClick={openPasswordModal}>修改密码</button>
                      </div>
                      <Button block type="primary" size="large" htmlType="submit" loading={submitting} icon={<ArrowRight size={17} />}>
                        进入工作台
                      </Button>
                    </Form>
                  ),
                },
                {
                  key: 'register',
                  label: '创建账号',
                  children: (
                    <Form layout="vertical" requiredMark={false} onFinish={(values) => run(() => register(values))}>
                      <div className="auth-form-grid">
                        <Form.Item label="用户名" name="username" rules={[{ required: true }, { min: 3 }, { max: 64 }]}>
                          <Input size="large" autoComplete="username" />
                        </Form.Item>
                        <Form.Item label="显示名称" name="displayName" rules={[{ required: true }, { max: 100 }]}>
                          <Input size="large" />
                        </Form.Item>
                      </div>
                      <Form.Item label="邮箱" name="email" rules={[{ required: true }, { type: 'email' }]}>
                        <Input size="large" autoComplete="email" />
                      </Form.Item>
                      <Form.Item label="密码" name="password" rules={[{ required: true }, { min: 12 }, { max: 72 }]}>
                        <Input.Password size="large" autoComplete="new-password" placeholder="至少 12 个字符" />
                      </Form.Item>
                      <Button block type="primary" size="large" htmlType="submit" loading={submitting} icon={<UserPlus size={17} />}>
                        创建个人知识空间
                      </Button>
                    </Form>
                  ),
                },
              ]}
            />
          )}
        </div>
      </section>

      <Modal
        title={<span className="password-modal-title"><KeyRound size={18} /> 修改登录密码</span>}
        open={passwordOpen}
        okText="确认修改"
        cancelText="取消"
        confirmLoading={submitting}
        onOk={() => passwordForm.submit()}
        onCancel={() => setPasswordOpen(false)}
      >
        <Form form={passwordForm} layout="vertical" requiredMark={false} onFinish={changePassword}>
          <Form.Item label="用户名" name="username" rules={[{ required: true }]}>
            <Input autoComplete="username" />
          </Form.Item>
          <Form.Item label="当前密码" name="currentPassword" rules={[{ required: true }]}>
            <Input.Password autoComplete="current-password" />
          </Form.Item>
          <Form.Item label="新密码" name="newPassword" rules={[{ required: true }, { min: 12 }, { max: 72 }]}>
            <Input.Password autoComplete="new-password" />
          </Form.Item>
          <Form.Item
            label="确认新密码"
            name="confirmPassword"
            dependencies={['newPassword']}
            rules={[
              { required: true },
              ({ getFieldValue }) => ({
                validator(_, value) {
                  return !value || getFieldValue('newPassword') === value
                    ? Promise.resolve() : Promise.reject(new Error('两次输入的新密码不一致'));
                },
              }),
            ]}
          >
            <Input.Password autoComplete="new-password" />
          </Form.Item>
          <Form.Item label="安全验证" required>
            <div className="captcha-row">
              <Form.Item noStyle name="captchaCode" rules={[{ required: true, message: '请输入验证码' }]}>
                <Input maxLength={4} placeholder="验证码" />
              </Form.Item>
              <button className="captcha-image" type="button" onClick={() => void loadCaptcha('password')}>
                {passwordCaptcha ? <img src={passwordCaptcha.imageDataUri} alt="密码修改验证码" /> : <RefreshCw size={17} />}
              </button>
            </div>
          </Form.Item>
        </Form>
      </Modal>
    </main>
  );
}
