// 展示名稱、英文副標題與圖片寫死；舊名稱只是 API 對照 key，不顯示在畫面。
// 保留活動 ID、庫存與既有訂單，不另建一批改名後的活動。
export const catalog = {
  "Kubernetes DevOps Concert": { title: "星光現場音樂祭", subtitle: "Starlight Music Festival", category: "music", label: "音樂現場", image: "starlight-music-festival-v2", description: "樂團演出與現場音樂" },
  "Cloud Native Summit": { title: "城市創意論壇", subtitle: "City Ideas Forum", category: "talk", label: "講座工作坊", image: "city-ideas-forum-v2", description: "創意觀點分享與交流" },
  "Spring Boot Workshop": { title: "數位創作工作坊", subtitle: "Digital Creative Workshop", category: "talk", label: "講座工作坊", image: "digital-creative-workshop-v2", description: "數位工具與創作實作" },
  "Indie Music Night": { title: "城市獨立音樂之夜", subtitle: "Indie Music Night", category: "music", label: "音樂現場", image: "indie-music-night-v2", description: "獨立樂團與現場音樂" },
  "Future Design Expo": { title: "未來設計靈感展", subtitle: "Future Design Expo", category: "culture", label: "藝文生活", image: "future-design-expo-v2", description: "設計作品與創作展示" },
  "Weekend Jazz Market": { title: "週末爵士生活市集", subtitle: "Weekend Jazz Market", category: "culture", label: "藝文生活", image: "weekend-jazz-market-v2", description: "爵士音樂與生活市集" },
};

export function presentation(name) {
  return catalog[name] || { title: name || "未命名活動", subtitle: "", category: "other", label: "精選活動", image: "future-design-expo-v2", description: "活動票券" };
}

export function escapeHtml(value) {
  return String(value ?? "").replace(/[&<>"']/g, char => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[char]));
}

export function statusLabel(status) {
  return ({ SUCCESS: "已成立", QUEUED: "處理中", PENDING: "處理中", FAILED: "未成立", CANCELLED: "已取消" })[status] || "狀態待確認";
}

export function formatTime(value) {
  // 後端 LocalDateTime 沒有時區，不擅自轉成 UTC。
  const match = String(value ?? "").match(/^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/);
  return match ? `${match[1]}.${match[2]}.${match[3]} · ${match[4]}:${match[5]}` : "時間未提供";
}
