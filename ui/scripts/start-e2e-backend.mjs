import { spawn } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url));
const backendDirectory = path.resolve(scriptDirectory, '../../backend');
const isWindows = process.platform === 'win32';
const mavenArguments = [
  '-q',
  '-Dmaven.repo.local=../.m2/repository',
  'spring-boot:run',
];

/**
 * 始终通过仓库内的构建工具包装器启动后端，避免开发机和持续集成环境的
 * Maven 版本不一致。Windows 的命令脚本需要通过系统命令解释器执行。
 */
const backendProcess = spawn(
  isWindows ? (process.env.ComSpec ?? 'cmd.exe') : 'sh',
  isWindows ? ['/d', '/s', '/c', 'mvnw.cmd', ...mavenArguments] : ['./mvnw', ...mavenArguments],
  {
    cwd: backendDirectory,
    env: process.env,
    stdio: 'inherit',
  },
);

let stopping = false;

function stopBackend(signal) {
  if (stopping) return;
  stopping = true;
  backendProcess.kill(signal);
}

process.on('SIGINT', () => stopBackend('SIGINT'));
process.on('SIGTERM', () => stopBackend('SIGTERM'));

backendProcess.on('error', (error) => {
  console.error('无法启动全链路测试后端：', error);
  process.exitCode = 1;
});

backendProcess.on('exit', (code, signal) => {
  process.exit(code ?? (signal ? 0 : 1));
});
