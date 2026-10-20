package com.hc.drown;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.hc.drown.module.device.service.DeviceService;
import com.hc.drown.module.device.entity.Device;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * 黄村乡AI防溺水智能管理平台 - 启动类
 */
@SpringBootApplication
@MapperScan("com.hc.drown.module.**.mapper")
@EnableScheduling
public class HcDrownApplication {

    @jakarta.annotation.PostConstruct
    public void initTimeZone() {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Shanghai"));
    }

    public static void main(String[] args) {
        SpringApplication.run(HcDrownApplication.class, args);
    }

    @Bean
    public CommandLineRunner initTestData(DeviceService deviceService, org.springframework.security.crypto.password.PasswordEncoder encoder, javax.sql.DataSource dataSource) {
        return args -> {
            String demoStreamUrl = System.getenv("DEMO_CAMERA_RTSP_URL");
            if (demoStreamUrl == null || demoStreamUrl.isBlank()) {
                return; // Demo camera seeding requires explicit local configuration.
            }
            try {
                // 打印当前所有用户信息并重置 admin 密码为 Admin@2024
                try (java.sql.Connection conn = dataSource.getConnection()) {
                    try (java.sql.Statement stmt = conn.createStatement()) {
                        try (java.sql.ResultSet rs = stmt.executeQuery("SELECT id, username, password FROM sys_user")) {
                            while (rs.next()) {
                                System.out.println("HcDrownApplication: 用户名=" + rs.getString("username") + ", 密码哈希=" + rs.getString("password"));
                            }
                        }
                        // 强制更新 admin 密码哈希为 Admin@2024
                        String newHash = encoder.encode("Admin@2024");
                        stmt.executeUpdate("UPDATE sys_user SET password = '" + newHash + "' WHERE username = 'admin'");
                        System.out.println("HcDrownApplication: 成功将 admin 密码重置为 Admin@2024, 新哈希=" + newHash);
                    }
                } catch (Exception dbEx) {
                    System.err.println("HcDrownApplication: 数据库连接/更新失败: " + dbEx.getMessage());
                }

                List<Device> cameras = deviceService.getDevicesByType("CAMERA");
                if (cameras.isEmpty()) {
                    Device cam = new Device();
                    cam.setDeviceCode("test1");
                    cam.setDeviceName("测试摄像头");
                    cam.setDeviceType("CAMERA");
                    cam.setIpAddress("10.150.62.200");
                    cam.setPort(554);
                    cam.setLocation("POINT(120.0304565 28.5064125)");
                    cam.setLocationDesc("黄村河道测试点");
                    cam.setWaterAreaId(1L);
                    cam.setStatus("ONLINE");
                    Map<String, Object> config = new HashMap<>();
                    config.put("streamUrl", demoStreamUrl);
                    cam.setExtraConfig(config);
                    deviceService.createDevice(cam);
                    System.out.println("HcDrownApplication: 成功在数据库中自动注册测试摄像头 test1");
                } else {
                    for (Device cam : cameras) {
                        Map<String, Object> config = cam.getExtraConfig();
                        if (config == null) {
                            config = new HashMap<>();
                        }
                        config.put("streamUrl", demoStreamUrl);
                        cam.setExtraConfig(config);
                        cam.setStatus("ONLINE");
                        deviceService.updateDevice(cam);
                        System.out.println("HcDrownApplication: 成功配置测试摄像头 " + cam.getDeviceCode() + " (ID=" + cam.getId() + ") 视频流地址为 [configured via DEMO_CAMERA_RTSP_URL]");
                    }
                }
            } catch (Exception e) {
                System.err.println("HcDrownApplication: 初始化测试流地址失败: " + e.getMessage());
            }
        };
    }
}
