package com.howe.common.utils.file;

/**
 * 媒体类型工具类
 *
 * @author howe
 */
public class MimeTypeUtils
{
    public static final String IMAGE_PNG = "image/png";

    public static final String IMAGE_JPG = "image/jpg";

    public static final String IMAGE_JPEG = "image/jpeg";

    public static final String IMAGE_BMP = "image/bmp";

    public static final String IMAGE_GIF = "image/gif";

    public static final String[] IMAGE_EXTENSION = { "bmp", "gif", "jpg", "jpeg", "png" };

    public static final String[] FLASH_EXTENSION = { "swf", "flv" };

    public static final String[] MEDIA_EXTENSION = { "swf", "flv", "mp3", "wav", "wma", "wmv", "mid", "avi", "mpg",
            "asf", "rm", "rmvb" };

    public static final String[] VIDEO_EXTENSION = { "mp4", "avi", "rmvb" };

    public static final String[] DEFAULT_ALLOWED_EXTENSION = {
            // 图片
            "bmp", "gif", "jpg", "jpeg", "png",
            // word excel powerpoint
            "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt",
            // 压缩文件
            "rar", "zip", "gz", "bz2",
            // 视频格式
            "mp4", "avi", "rmvb",
            // pdf
            "pdf" };

    public static String getExtension(String prefix)
    {
        switch (prefix)
        {
            case IMAGE_PNG:
                return "png";
            case IMAGE_JPG:
                return "jpg";
            case IMAGE_JPEG:
                return "jpeg";
            case IMAGE_BMP:
                return "bmp";
            case IMAGE_GIF:
                return "gif";
            default:
                return "";
        }
    }

    /**
     * 按扩展名推断 MIME 类型
     *
     * <p>
     * 上传到对象存储时必须带正确的 Content-Type，否则图床返回 application/octet-stream，
     * 浏览器会触发下载而不是内联展示图片。
     * </p>
     *
     * @param extension 扩展名，不带点，大小写不敏感
     * @return MIME 类型，无法识别时返回空串
     */
    public static String getContentType(String extension)
    {
        if (extension == null || extension.isEmpty())
        {
            return "";
        }
        switch (extension.toLowerCase())
        {
            case "png":
                return IMAGE_PNG;
            case "jpg":
            case "jpeg":
                return IMAGE_JPEG;
            case "bmp":
                return IMAGE_BMP;
            case "gif":
                return IMAGE_GIF;
            case "webp":
                return "image/webp";
            case "svg":
                return "image/svg+xml";
            case "avif":
                return "image/avif";
            case "ico":
                return "image/x-icon";
            case "txt":
                return "text/plain; charset=utf-8";
            case "md":
                return "text/markdown; charset=utf-8";
            case "pdf":
                return "application/pdf";
            case "zip":
                return "application/zip";
            case "gz":
                return "application/gzip";
            case "rar":
                return "application/vnd.rar";
            case "bz2":
                return "application/x-bzip2";
            case "mp4":
                return "video/mp4";
            case "avi":
                return "video/x-msvideo";
            case "rmvb":
                return "application/vnd.rn-realmedia-vbr";
            case "mp3":
                return "audio/mpeg";
            case "wav":
                return "audio/wav";
            case "doc":
                return "application/msword";
            case "docx":
                return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls":
                return "application/vnd.ms-excel";
            case "xlsx":
                return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "ppt":
                return "application/vnd.ms-powerpoint";
            case "pptx":
                return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            default:
                return "";
        }
    }
}
