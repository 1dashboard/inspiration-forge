package io.github.onedashboard.inspirationforge.manager;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.PutObjectResult;
import io.github.onedashboard.inspirationforge.config.CosClientConfig;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import com.qcloud.cos.model.DeleteObjectRequest;

/**
 * COS 对象存储管理器
 */
@Component
@Slf4j
public class CosManager {

    @Resource
    private CosClientConfig cosClientConfig;

    @Resource
    private COSClient cosClient;

    /**
     * 上传对象
     *
     * @param key  唯一键
     * @param file 文件
     * @return 上传结果
     */
    public PutObjectResult putObject(String key, File file) {
        PutObjectRequest putObjectRequest = new PutObjectRequest(cosClientConfig.getBucket(), key, file);
        return cosClient.putObject(putObjectRequest);
    }

    /**
     * 上传文件到 COS 并返回访问 URL
     *
     * @param key  COS对象键（完整路径）
     * @param file 要上传的文件
     * @return 文件的访问URL，失败返回null
     */
    public String uploadFile(String key, File file) {
        PutObjectResult result = putObject(key, file);
        if (result != null) {
            String url = String.format("%s%s", cosClientConfig.getHost(), key);
            log.info("文件上传到 COS 成功：{} -> {}", file.getName(), url);
            return url;
        } else {
            log.error("文件上传到 COS 失败：{}，返回结果为空", file.getName());
            return null;
        }
    }

    public void deleteObject(String key) {
        if (key == null || key.isBlank()) return;
        cosClient.deleteObject(new DeleteObjectRequest(cosClientConfig.getBucket(), key));
    }

    /** 根据配置的 COS 域名删除对象，避免业务层硬编码存储地址。 */
    public void deleteByUrl(String url) {
        if (url == null || url.isBlank() || cosClientConfig.getHost() == null) return;
        String host = cosClientConfig.getHost();
        if (!url.startsWith(host)) return;
        String key = url.substring(host.length());
        while (key.startsWith("/")) {
            key = key.substring(1);
        }
        deleteObject(key);
    }
}
