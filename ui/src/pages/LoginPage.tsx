import { Button, Form, Input, Tabs, message } from 'antd';
import { BookOpenText, LogIn, UserPlus } from 'lucide-react';
import { useState } from 'react';
import { useAppSession } from '../contexts/AppSessionContext';
import { env } from '../config/env';

/** 本地 JWT 开发与私有部署使用的登录、注册入口。 */
export function LoginPage() {
  const { login, loginWithOidc, register } = useAppSession();
  const [submitting, setSubmitting] = useState(false);

  const run = async (action: () => Promise<void>) => {
    setSubmitting(true);
    try {
      await action();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '认证失败');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <main className="auth-page">
      <section className="auth-panel">
        <header className="auth-brand">
          <BookOpenText size={30} />
          <div><strong>AgentMind</strong><span>个人知识 Agent</span></div>
        </header>
        {env.authMode === 'oidc' ? (
          <Button
            block
            type="primary"
            loading={submitting}
            icon={<LogIn size={16} />}
            onClick={() => run(loginWithOidc)}
          >
            使用身份提供方登录
          </Button>
        ) : <Tabs
          items={[
            {
              key: 'login',
              label: '登录',
              children: (
                <Form layout="vertical" onFinish={(values) => run(() => login(values))}>
                  <Form.Item label="用户名" name="username" rules={[{ required: true, message: '请输入用户名' }]}>
                    <Input autoComplete="username" />
                  </Form.Item>
                  <Form.Item label="密码" name="password" rules={[{ required: true, message: '请输入密码' }]}>
                    <Input.Password autoComplete="current-password" />
                  </Form.Item>
                  <Button block type="primary" htmlType="submit" loading={submitting} icon={<LogIn size={16} />}>
                    登录
                  </Button>
                </Form>
              ),
            },
            {
              key: 'register',
              label: '注册',
              children: (
                <Form layout="vertical" onFinish={(values) => run(() => register(values))}>
                  <Form.Item label="用户名" name="username" rules={[{ required: true }, { min: 3 }, { max: 64 }]}>
                    <Input autoComplete="username" />
                  </Form.Item>
                  <Form.Item label="显示名称" name="displayName" rules={[{ required: true }, { max: 100 }]}>
                    <Input />
                  </Form.Item>
                  <Form.Item label="邮箱" name="email" rules={[{ required: true }, { type: 'email' }]}>
                    <Input autoComplete="email" />
                  </Form.Item>
                  <Form.Item label="密码" name="password" rules={[{ required: true }, { min: 12 }, { max: 72 }]}>
                    <Input.Password autoComplete="new-password" />
                  </Form.Item>
                  <Button block type="primary" htmlType="submit" loading={submitting} icon={<UserPlus size={16} />}>
                    创建账号
                  </Button>
                </Form>
              ),
            },
          ]}
        />}
      </section>
    </main>
  );
}
