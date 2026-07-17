# PDF 与 Word 文档解析联调手册

## 支持范围

当前文件摄取链路支持以下二进制文档：

| 格式 | 解析实现 | 当前能力 |
| --- | --- | --- |
| PDF | Apache PDFBox 3.0.8 | 提取文档标题和文本层正文 |
| DOC | Apache Tika 3.3.1 与 Apache POI | 识别 OLE2 Word、RTF 等 Word 兼容内容 |
| DOCX | Apache Tika 3.3.1 与 Apache POI | 提取 OOXML 标题和段落正文 |

PDF 使用独立解析器，便于后续增加页码定位、页眉页脚去重和分页引用。Word 使用 Tika 自动检测真实内容格式，不信任客户端上传的 MIME 类型。

## 摄取链路

上传成功后的处理顺序如下：

1. 校验文件名、扩展名、原始字节大小和知识空间写权限。
2. 将原始文件保存到本地存储或 MinIO。
3. 根据文档来源类型选择 PDFBox 或 Tika 解析器。
4. 规范化换行、空白和标题。
5. 拒绝损坏、加密、超限或没有可索引文本的文件。
6. 将正文切分为 chunk。
7. 批量生成 Embedding。
8. 写入向量索引和关键词索引。
9. 更新文档元数据与摄取任务终态。

解析失败时文档和任务都会标记为 `FAILED`，不会生成空 chunk，也不会保留旧向量索引。

## 资源限制

```yaml
agentmind:
  ingestion:
    max-upload-size-bytes: 20971520
    parsing:
      max-pdf-pages: 500
      max-extracted-characters: 2000000
```

生产环境可以通过以下变量调整：

```text
AGENTMIND_PARSING_MAX_PDF_PAGES
AGENTMIND_PARSING_MAX_CHARACTERS
```

不要只提高限制而不评估摄取工作线程、堆内存、任务超时和并发上传数量。

## 当前限制

- 扫描图片 PDF 没有文本层，当前会明确提示需要 OCR，不会伪装为摄取成功。
- 加密 PDF 和受密码保护的 Office 文档当前不接受密码输入。
- PDF 暂未做重复页眉页脚和页码清理。
- DOC/DOCX 暂不保留表格坐标、批注、修订记录和原始段落样式。
- 当前 chunk 只保存字符位置，尚未保存 PDF 页码。

这些限制不影响普通技术文档、电子版 PDF、课程讲义和 Word 学习笔记的文本检索。

## 手动测试

启动后端后，通过采集中心分别上传一个带文本层的 PDF、一个 `.doc` 和一个 `.docx`。任务完成后检查：

1. 摄取任务状态为 `SUCCEEDED`。
2. 文档列表中的 `chunkCount` 大于零。
3. 文档片段接口能够看到提取后的正文。
4. `/knowledge/search` 能检索到文档中的唯一关键词。
5. RAG 回答能够引用对应文档片段。

上传扫描 PDF 或损坏文件时，应看到稳定中文错误，并在任务列表中看到 `FAILED`，不能出现成功但片段数为零的状态。

## 自动化测试

```powershell
Set-Location D:\Program\AgentMind\backend
.\mvnw.cmd "-Dtest=PdfTextExtractorTests,WordTextExtractorTests,DocumentTextExtractionServiceTests,DocumentApplicationServiceTests" test
```

测试会在内存中生成真实 PDF 和 DOCX，并验证 Word 兼容 DOC 内容、页数限制、扫描 PDF、损坏 PDF、chunk 生成和向量检索，不依赖网络或本机 Office 软件。
