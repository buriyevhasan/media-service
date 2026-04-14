package com.mediaservice.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.util.Arrays;

@Slf4j
@Component
public class StartupBanner {

    private final Environment env;

    public StartupBanner(Environment env) {
        this.env = env;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {

        try {

            Thread.sleep(5000);

            // Port va context-path
            String port = env.getProperty("local.server.port",
                    env.getProperty("server.port", "8080"));
            String contextPath = env.getProperty("server.servlet.context-path", "");
            if (contextPath.equals("/")) contextPath = "";
            // IP (External URL uchun)
            String host = InetAddress.getLocalHost().getHostAddress();

            // Profil(lar)
            String[] profiles = env.getActiveProfiles();
            String profilesStr = profiles.length == 0 ? "default" : Arrays.toString(profiles);

            // DB URL
            String dbUrl = "n/a";
            // Vaqt
            String startedAt = OffsetDateTime.now().toString();

            String appName = env.getProperty("spring.application.name", "UzbekChemicalIndustry");

            log.info("\n----------------------------------------------------------\n" +
                            "\tApplication '{}' is running!\n" +
                            "\tAccess URLs:\n" +
                            "\tLocal:\t\t\thttp://localhost:{}{}/\n" +
                            "\tExternal:\t\thttp://{}:{}{}/\n" +
                            "\tStarted at:\t\t{}\n" +
                            "\tProfile(s):\t\t{}\n" +
                            "\tDATABASE(s):\t{}\n" +
//                            "\tRunning time:\t\t{} " + "milliseconds" +
                            "----------------------------------------------------------\n",
                    appName, port, contextPath,
                    host, port, contextPath,
                    startedAt,
                    profilesStr,
                    dbUrl
//                    System.currentTimeMillis() - FinanceKoApplication.startTime
            );
        } catch (Exception e) {
            log.warn("Startup banner chiqarishda xatolik: {}", e.getMessage(), e);
        }
    }
}
