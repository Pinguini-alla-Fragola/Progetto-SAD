package it.unina.picus.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSender;
//Scusa ale ma altrimenti caprio non si stava zitto
class ConfigurazioneInvioEmailTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ConfigurazioneInvioEmail.class)
            .withBean(JavaMailSender.class, () -> mock(JavaMailSender.class));

    @Test
    void conHostSmtpEProfiloTest_selezionaSoloLogAdapter() {
        contextRunner
                .withPropertyValues("spring.mail.host=smtp.esempio.it")
                .withSystemProperties()
                .withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("test"))
                .run(context -> {
                    assertThat(context).hasSingleBean(InvioEmail.class);
                    assertThat(context).hasSingleBean(InvioEmailLogAdapter.class);
                    assertThat(context).doesNotHaveBean(InvioEmailSmtpAdapter.class);
                });
    }

    @Test
    void conHostSmtpEProfiloDevelopment_selezionaSoloSmtpAdapter() {
        contextRunner
                .withPropertyValues("spring.mail.host=smtp.esempio.it")
                .withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("development"))
                .run(context -> {
                    assertThat(context).hasSingleBean(InvioEmail.class);
                    assertThat(context).hasSingleBean(InvioEmailSmtpAdapter.class);
                    assertThat(context).doesNotHaveBean(InvioEmailLogAdapter.class);
                });
    }

    @Test
    void conHostSmtpEProfiloDev_selezionaSoloLogAdapter() {
        contextRunner
                .withPropertyValues("spring.mail.host=smtp.esempio.it")
                .withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("dev"))
                .run(context -> {
                    assertThat(context).hasSingleBean(InvioEmail.class);
                    assertThat(context).hasSingleBean(InvioEmailLogAdapter.class);
                });
    }

    @Test
    void senzaHostSmtp_selezionaSoloLogAdapter() {
        contextRunner
                .run(context -> {
                    assertThat(context).hasSingleBean(InvioEmail.class);
                    assertThat(context).hasSingleBean(InvioEmailLogAdapter.class);
                });
    }
}