# 活動圖片生成紀錄

## 目前使用的 v2 自然照片（2026-10-04）

模式：內建 imagegen，六張各自重新生成，不是對舊圖降飽和度或套黑白濾鏡。
方向：普通相機拍到的活動現場，保留正常膚色、藍色、綠色與材質色，使用自然光線及柔和對比，避免霓虹、HDR、橙藍電影調色與鮮豔宣傳海報感。

專案使用下列六張 `-v2.webp`。從生成的 1536 × 1024 PNG 縮為 960 × 640，WebP 品質 85，六張合計 538,764 bytes（約 526 KiB）。只做尺寸與格式轉換，沒有色彩加工；CSS 的 `--photo-filter: none` 也不再另外降飽和度。活動封面和訂單縮圖共用同一份對照。

工具的原始 PNG 留在原生成目錄，舊版六張 WebP 也完整保留，沒有刪除或覆寫。這些仍是虛構活動的生成配圖，不代表真實活動紀錄；此實作說明只留在專案文件，不加到正常網頁。

### 星光現場音樂祭：starlight-music-festival-v2.webp

來源 PNG：`C:\Users\mark4\.codex\generated_images\01a0f347-dc49-7400-b4ad-b2088cfde869\exec-fdbb61df-b8e7-4d8b-8313-e167e6880de8.png`

專案檔案：`frontend/images/starlight-music-festival-v2.webp`

完整生成提示詞：

```text
Use case: photorealistic-natural
Asset type: individual full-bleed activity cover photograph for a ticket website.
Style/medium: photorealistic candid documentary event photograph, like an ordinary real camera JPEG with a neutral picture profile. Real fabric wear, skin texture, imperfect everyday surroundings, believable human proportions.
Composition/framing: landscape 3:2, eye-level wide framing, important action near the middle so the photo also works when vertically cropped to a shallow horizontal card. One complete photograph, not a collage.
Color and lighting: natural full color with restrained everyday saturation, neutral white balance, soft realistic contrast. Preserve believable skin tones, subtle blues, greens and material colors. NOT black-and-white, NOT sepia, NOT a uniformly gray or washed-out image.
Avoid: HDR, oversaturation, neon colors, orange-and-teal cinematic grading, golden-hour orange wash, glowing highlights, dramatic god rays, fake smoke, extreme bokeh, 3D render, glossy AI advertising look, futuristic stage sculptures, perfect staged symmetry.
Constraints: no readable words, event titles, logos, watermarks, borders or UI. Fictional event image, not a claim to document an actual named event.
Scene/subject: a modest outdoor live music festival at early evening in a Taiwanese urban park. A small three-person band playing on a practical low stage, audience seen naturally from behind. Blue-gray dusk sky, a few neutral white stage lamps, ordinary navy and beige clothing, natural brown wooden guitar. Low-key event atmosphere with a little real color, no colored spotlights or elaborate decorative lighting.
```

### 城市創意論壇：city-ideas-forum-v2.webp

來源 PNG：`C:\Users\mark4\.codex\generated_images\01a0f347-dc49-7400-b4ad-b2088cfde869\exec-bc2beda1-25c1-4484-b03e-2760423872ea.png`

專案檔案：`frontend/images/city-ideas-forum-v2.webp`

完整生成提示詞：

```text
Use case: photorealistic-natural
Asset type: individual full-bleed activity cover photograph for a ticket website.
Style/medium: photorealistic candid documentary event photograph, like an ordinary real camera JPEG with a neutral picture profile. Real fabric wear, skin texture, imperfect everyday surroundings, believable human proportions.
Composition/framing: landscape 3:2, eye-level wide framing, important action near the middle so the photo also works when vertically cropped to a shallow horizontal card. One complete photograph, not a collage.
Color and lighting: natural full color with restrained everyday saturation, neutral white balance, soft realistic contrast. Preserve believable skin tones, subtle blues, greens and material colors. NOT black-and-white, NOT sepia, NOT a uniformly gray or washed-out image.
Avoid: HDR, oversaturation, neon colors, orange-and-teal cinematic grading, golden-hour orange wash, glowing highlights, dramatic god rays, fake smoke, extreme bokeh, 3D render, glossy AI advertising look, futuristic stage sculptures, perfect staged symmetry.
Constraints: no readable words, event titles, logos, watermarks, borders or UI. Fictional event image, not a claim to document an actual named event.
Scene/subject: a community creative-ideas forum in a modest contemporary lecture hall. A speaker and two seated panelists on a simple stage, listeners in ordinary casual clothing seen from the rear. Off-white walls, natural timber details, muted blue seating, soft neutral indoor lighting mixed with daylight. A plain dark projection screen without readable content. Believable local event, no gigantic cloud-shaped installation, no futuristic architecture or bright colored stage lights.
```

### 數位創作工作坊：digital-creative-workshop-v2.webp

來源 PNG：`C:\Users\mark4\.codex\generated_images\01a0f347-dc49-7400-b4ad-b2088cfde869\exec-e2fffd5a-43a3-4f52-9058-6f556623d723.png`

專案檔案：`frontend/images/digital-creative-workshop-v2.webp`

完整生成提示詞：

```text
Use case: photorealistic-natural
Asset type: individual full-bleed activity cover photograph for a ticket website.
Style/medium: photorealistic candid documentary event photograph, like an ordinary real camera JPEG with a neutral picture profile. Real fabric wear, skin texture, imperfect everyday surroundings, believable human proportions.
Composition/framing: landscape 3:2, eye-level wide framing, important action near the middle so the photo also works when vertically cropped to a shallow horizontal card. One complete photograph, not a collage.
Color and lighting: natural full color with restrained everyday saturation, neutral white balance, soft realistic contrast. Preserve believable skin tones, subtle blues, greens and material colors. NOT black-and-white, NOT sepia, NOT a uniformly gray or washed-out image.
Avoid: HDR, oversaturation, neon colors, orange-and-teal cinematic grading, golden-hour orange wash, glowing highlights, dramatic god rays, fake smoke, extreme bokeh, 3D render, glossy AI advertising look, futuristic stage sculptures, perfect staged symmetry.
Constraints: no readable words, event titles, logos, watermarks, borders or UI. Fictional event image, not a claim to document an actual named event.
Scene/subject: three adults taking part in a digital creative workshop at a shared wooden table, photographed candidly across their shoulders. One participant works on a laptop displaying a small photographic image without readable text, notebooks, pencils and a couple of ordinary mugs around it. Soft diffuse window light through a curtain, ordinary beige and muted blue clothes, a small naturally green plant. Real casual working room and slightly imperfect arrangement, not a showroom or a coding advertisement.
```

### 城市獨立音樂之夜：indie-music-night-v2.webp

來源 PNG：`C:\Users\mark4\.codex\generated_images\01a0f347-dc49-7400-b4ad-b2088cfde869\exec-fa03cfad-f459-4a2b-9871-6df6ec4bfaa9.png`

專案檔案：`frontend/images/indie-music-night-v2.webp`

完整生成提示詞：

```text
Use case: photorealistic-natural
Asset type: individual full-bleed activity cover photograph for a ticket website.
Style/medium: photorealistic candid documentary event photograph, like an ordinary real camera JPEG with a neutral picture profile. Real fabric wear, skin texture, imperfect everyday surroundings, believable human proportions.
Composition/framing: landscape 3:2, eye-level wide framing, important action near the middle so the photo also works when vertically cropped to a shallow horizontal card. One complete photograph, not a collage.
Color and lighting: natural full color with restrained everyday saturation, neutral white balance, soft realistic contrast. Preserve believable skin tones, subtle blues, greens and material colors. NOT black-and-white, NOT sepia, NOT a uniformly gray or washed-out image.
Avoid: HDR, oversaturation, neon colors, orange-and-teal cinematic grading, golden-hour orange wash, glowing highlights, dramatic god rays, fake smoke, extreme bokeh, 3D render, glossy AI advertising look, futuristic stage sculptures, perfect staged symmetry.
Constraints: no readable words, event titles, logos, watermarks, borders or UI. Fictional event image, not a claim to document an actual named event.
Scene/subject: an intimate independent-band performance in a small urban live-music venue. Guitarist in a muted navy shirt, bassist, and drummer with worn natural wooden instruments. A few audience silhouettes at the bottom. Simple neutral white performance lighting with only a trace of ordinary warm room light. Dark but properly exposed full-color photograph, realistic skin, instrument and denim colors. No red or purple floodlights, no haze or laser beams.
```

### 未來設計靈感展：future-design-expo-v2.webp

來源 PNG：`C:\Users\mark4\.codex\generated_images\01a0f347-dc49-7400-b4ad-b2088cfde869\exec-58b59203-7fb1-4ff7-b3ac-8b13123f69dd.png`

專案檔案：`frontend/images/future-design-expo-v2.webp`

完整生成提示詞：

```text
Use case: photorealistic-natural
Asset type: individual full-bleed activity cover photograph for a ticket website.
Style/medium: photorealistic candid documentary event photograph, like an ordinary real camera JPEG with a neutral picture profile. Real fabric wear, skin texture, imperfect everyday surroundings, believable human proportions.
Composition/framing: landscape 3:2, eye-level wide framing, important action near the middle so the photo also works when vertically cropped to a shallow horizontal card. One complete photograph, not a collage.
Color and lighting: natural full color with restrained everyday saturation, neutral white balance, soft realistic contrast. Preserve believable skin tones, subtle blues, greens and material colors. NOT black-and-white, NOT sepia, NOT a uniformly gray or washed-out image.
Avoid: HDR, oversaturation, neon colors, orange-and-teal cinematic grading, golden-hour orange wash, glowing highlights, dramatic god rays, fake smoke, extreme bokeh, 3D render, glossy AI advertising look, futuristic stage sculptures, perfect staged symmetry.
Constraints: no readable words, event titles, logos, watermarks, borders or UI. Fictional event image, not a claim to document an actual named event.
Scene/subject: a small contemporary design exhibition in a real bright cultural-center gallery. Matte clay ceramics, a terracotta vase, small wood and fabric design objects, framed abstract designs without readable lettering. Two visitors casually looking at the work. Soft diffuse daylight, off-white walls, neutral plinths and subtle natural blue and earthy tones. Realistic object scale, craftsmanship texture and imperfect gallery details, not a monumental CGI sculpture showroom.
```

### 週末爵士生活市集：weekend-jazz-market-v2.webp

來源 PNG：`C:\Users\mark4\.codex\generated_images\01a0f347-dc49-7400-b4ad-b2088cfde869\exec-40430b97-6a70-47d4-b6ec-0f1471de4362.png`

專案檔案：`frontend/images/weekend-jazz-market-v2.webp`

完整生成提示詞：

```text
Use case: photorealistic-natural
Asset type: individual full-bleed activity cover photograph for a ticket website.
Style/medium: photorealistic candid documentary event photograph, like an ordinary real camera JPEG with a neutral picture profile. Real fabric wear, skin texture, imperfect everyday surroundings, believable human proportions.
Composition/framing: landscape 3:2, eye-level wide framing, important action near the middle so the photo also works when vertically cropped to a shallow horizontal card. One complete photograph, not a collage.
Color and lighting: natural full color with restrained everyday saturation, neutral white balance, soft realistic contrast. Preserve believable skin tones, subtle blues, greens and material colors. NOT black-and-white, NOT sepia, NOT a uniformly gray or washed-out image.
Avoid: HDR, oversaturation, neon colors, orange-and-teal cinematic grading, golden-hour orange wash, glowing highlights, dramatic god rays, fake smoke, extreme bokeh, 3D render, glossy AI advertising look, futuristic stage sculptures, perfect staged symmetry.
Constraints: no readable words, event titles, logos, watermarks, borders or UI. Fictional event image, not a claim to document an actual named event.
Scene/subject: a relaxed neighborhood weekend craft and coffee market in a Taiwanese urban courtyard, with a small live jazz trio including a saxophone player and visitors strolling between beige canvas stalls. Soft overcast mid-morning daylight, natural green leaves, faded brick paving, ordinary brown wood, blue and cream clothing. An unposed everyday street photograph with believable small-market scale. No golden sunset wash, no exaggerated colorful produce, no Mediterranean fantasy styling.
```

## 舊版 v1：保留的歷史生成紀錄


模式：內建 imagegen；六張獨立生成。原始 PNG 保留在 Codex 的 generated_images；專案使用寬 960px、品質 83 的 WebP，六張合計約 520 KiB。

這些是虛構示範活動的宣傳示意圖片，不代表真實活動現場。不使用外部圖片網址、品牌 Logo 或可讀文字。

## devops-concert.webp

```text
Use case: photorealistic-natural
Asset type: fictional event cover for a real ticket booking demo website.
Primary request: A small live concert stage with a contemporary geometric light installation evoking interconnected cloud computing nodes, audience silhouettes, coral and warm amber spotlights, dark navy backdrop.
Style/medium: photorealistic editorial event photography, consistent premium magazine treatment, natural texture and subtle film grain.
Composition/framing: landscape 3:2, generous wide framing that can crop to a shallow horizontal card; key subject within central 60%, full-bleed image only.
Lighting/mood: inviting, believable and tasteful.
Constraints: single photograph, no montage, no text, no logos, no watermark, no UI, no borders. Fictional event promotional visual, not a real event claim.
```

## cloud-native-summit.webp

```text
Use case: photorealistic-natural
Asset type: fictional event cover for a real ticket booking demo website.
Primary request: A contemporary developer conference auditorium, geometric cloud-inspired stage installation, teal lighting and warm sunlit architectural details, a few audience silhouettes; no readable screens.
Style/medium: photorealistic editorial event photography, consistent premium magazine treatment, natural texture and subtle film grain.
Composition/framing: landscape 3:2, generous wide framing that can crop to a shallow horizontal card; key subject within central 60%, full-bleed image only.
Lighting/mood: inviting, believable and tasteful.
Constraints: single photograph, no montage, no text, no logos, no watermark, no UI, no borders. Fictional event promotional visual, not a real event claim.
```

## spring-boot-workshop.webp

```text
Use case: photorealistic-natural
Asset type: fictional event cover for a real ticket booking demo website.
Primary request: A welcoming coding workshop, close-up desk with an open laptop showing abstract unrecognizable code, notebook and small plant, soft afternoon sunlight, sage green and ivory.
Style/medium: photorealistic editorial event photography, consistent premium magazine treatment, natural texture and subtle film grain.
Composition/framing: landscape 3:2, generous wide framing that can crop to a shallow horizontal card; key subject within central 60%, full-bleed image only.
Lighting/mood: inviting, believable and tasteful.
Constraints: single photograph, no montage, no text, no logos, no watermark, no UI, no borders. Fictional event promotional visual, not a real event claim.
```

## indie-music-night.webp

```text
Use case: photorealistic-natural
Asset type: fictional event cover for a real ticket booking demo website.
Primary request: An intimate indie band performance, guitarist and drum kit silhouetted against muted coral and burgundy stage lights, warm film texture, energetic but refined.
Style/medium: photorealistic editorial event photography, consistent premium magazine treatment, natural texture and subtle film grain.
Composition/framing: landscape 3:2, generous wide framing that can crop to a shallow horizontal card; key subject within central 60%, full-bleed image only.
Lighting/mood: inviting, believable and tasteful.
Constraints: single photograph, no montage, no text, no logos, no watermark, no UI, no borders. Fictional event promotional visual, not a real event claim.
```

## future-design-expo.webp

```text
Use case: photorealistic-natural
Asset type: fictional event cover for a real ticket booking demo website.
Primary request: A modern design exhibition in a warm white gallery, sculptural orange and cobalt geometric objects on plinths, striking architectural shadows, refined editorial composition.
Style/medium: photorealistic editorial event photography, consistent premium magazine treatment, natural texture and subtle film grain.
Composition/framing: landscape 3:2, generous wide framing that can crop to a shallow horizontal card; key subject within central 60%, full-bleed image only.
Lighting/mood: inviting, believable and tasteful.
Constraints: single photograph, no montage, no text, no logos, no watermark, no UI, no borders. Fictional event promotional visual, not a real event claim.
```

## weekend-jazz-market.webp

```text
Use case: photorealistic-natural
Asset type: fictional event cover for a real ticket booking demo website.
Primary request: A cozy outdoor weekend craft and coffee market with a saxophone performer, canvas stalls, plants and warm golden-hour light, natural human silhouettes, Mediterranean sage and ochre tones.
Style/medium: photorealistic editorial event photography, consistent premium magazine treatment, natural texture and subtle film grain.
Composition/framing: landscape 3:2, generous wide framing that can crop to a shallow horizontal card; key subject within central 60%, full-bleed image only.
Lighting/mood: inviting, believable and tasteful.
Constraints: single photograph, no montage, no text, no logos, no watermark, no UI, no borders. Fictional event promotional visual, not a real event claim.
```
