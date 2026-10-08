export function validateLoad(count, concurrency, duplicate = false) {
  if (!Number.isInteger(count) || count < 1 || count > (duplicate ? 10 : 500)) throw new Error(duplicate ? "重複測試請輸入 1～10 次。" : "請求數請輸入 1～500。");
  if (!Number.isInteger(concurrency) || concurrency < 1 || concurrency > 10) throw new Error("同時送出請輸入 1～10。");
}

export function loadCommand(origin, run) {
  validateLoad(run.count, run.concurrency, run.duplicate);
  const user = run.duplicate ? run.prefix + "same" : run.prefix + "{}";
  return `seq 1 ${run.count} | xargs -P ${run.concurrency} -I {} \\\n  curl --max-time 20 -sS -X POST '${origin}/api/events/${run.eventId}/buy?userId=${user}'`;
}

export async function runRequests(run, { shouldStop, request, progress }) {
  validateLoad(run.count, run.concurrency, run.duplicate);
  let next = 1;
  const worker = async () => {
    while (!shouldStop() && next <= run.count) {
      const index = next++;
      const user = run.prefix + (run.duplicate ? "same" : index);
      run.sent++;
      try {
        const result = await request(`/api/events/${run.eventId}/buy?userId=${encodeURIComponent(user)}`);
        if (result.success === true && result.status === "QUEUED") run.queued++;
        else if (result.success === true) run.accepted++;
        else if (result.success === false) run.rejected++;
        else run.uncertain++;
      } catch { run.uncertain++; }
      run.completed++;
      progress();
    }
  };
  await Promise.all(Array.from({ length: run.concurrency }, worker));
}
