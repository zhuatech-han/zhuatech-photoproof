// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.photoproof;

/** 原子导入返回安全行号，不返回客户内容或SQL。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
public class ImportProblem extends Problem {
  public final int row;

  /** 标记含表头的CSV失败行。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public ImportProblem(int status, String code, int row) {
    super(status, code);
    this.row = row;
  }
}
