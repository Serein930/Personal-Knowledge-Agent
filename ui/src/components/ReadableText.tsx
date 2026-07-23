interface ReadableTextProps {
  content: string;
  className?: string;
}

function readableLines(content: string) {
  const sourceLines = content.replace(/\r\n/g, '\n').split('\n');
  const result: string[] = [];
  sourceLines.forEach((sourceLine) => {
    const line = sourceLine.trim();
    if (!line) {
      result.push('');
      return;
    }
    if (line.length <= 180 || /^#{1,4}\s|^[-*]\s|^\d+[.、]\s*/.test(line)) {
      result.push(line);
      return;
    }
    const sentences = line.match(/[^。！？!?；;]+[。！？!?；;]?/g) ?? [line];
    let paragraph = '';
    sentences.forEach((sentence) => {
      const normalized = sentence.trim();
      if (paragraph && paragraph.length + normalized.length > 180) {
        result.push(paragraph);
        paragraph = normalized;
      } else {
        paragraph += normalized;
      }
    });
    if (paragraph) result.push(paragraph);
  });
  return result;
}

/**
 * 对模型文本做轻量排版，不执行 HTML，从而兼顾可读性与内容安全。
 * 长单行回答会按完整句子分段，Markdown 标题和列表符号则转换为结构化元素。
 */
export function ReadableText({ content, className }: ReadableTextProps) {
  return (
    <div className={className}>
      {readableLines(content).map((line, index) => {
        if (!line) return <div className="readable-text__space" key={`space-${index}`} />;
        const heading = line.match(/^#{1,4}\s+(.+)$/);
        if (heading) return <strong className="readable-text__heading" key={`heading-${index}`}>{heading[1]}</strong>;
        const bullet = line.match(/^[-*]\s+(.+)$/);
        if (bullet) return <div className="readable-text__bullet" key={`bullet-${index}`}><span>•</span><p>{bullet[1]}</p></div>;
        return <p key={`paragraph-${index}`}>{line}</p>;
      })}
    </div>
  );
}
