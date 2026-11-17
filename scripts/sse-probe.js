// SSE 探针：连接审核任务进度流，把事件逐行写入 JSONL，complete/error 或超时后退出。
// 用法：node scripts/sse-probe.js <taskId> <outFile> [timeoutSeconds]
const http = require('http');
const fs = require('fs');

const [taskId, outFile, timeoutArg] = process.argv.slice(2);
if (!taskId || !outFile) {
  console.error('usage: node sse-probe.js <taskId> <outFile> [timeoutSeconds]');
  process.exit(2);
}
const timeoutSeconds = Number(timeoutArg || 420);
const stream = fs.createWriteStream(outFile, { flags: 'w' });
let buffer = '';
let eventName = 'message';
let finished = false;

function done(code) {
  if (finished) return;
  finished = true;
  stream.end();
  process.exit(code);
}

const request = http.get(`http://127.0.0.1:8080/api/v1/audit/tasks/${taskId}/stream`, (response) => {
  if (response.statusCode !== 200) {
    console.error(`HTTP ${response.statusCode}`);
    done(1);
    return;
  }
  response.setEncoding('utf8');
  response.on('data', (chunk) => {
    buffer += chunk;
    let index;
    while ((index = buffer.indexOf('\n')) >= 0) {
      const line = buffer.slice(0, index).replace(/\r$/, '');
      buffer = buffer.slice(index + 1);
      if (line.startsWith('event:')) {
        eventName = line.slice(6).trim();
      } else if (line.startsWith('data:')) {
        const payload = line.slice(5).trim();
        stream.write(JSON.stringify({ event: eventName, data: payload }) + '\n');
        console.log(`[${eventName}] ${payload}`);
        if (eventName === 'complete' || eventName === 'error') {
          response.destroy();
          done(0);
        }
      }
    }
  });
});

const timer = setTimeout(() => {
  console.error(`timeout after ${timeoutSeconds}s`);
  request.destroy();
  done(3);
}, timeoutSeconds * 1000);

request.on('error', (error) => {
  if (!finished) {
    console.error(String(error));
    clearTimeout(timer);
    done(1);
  }
});
