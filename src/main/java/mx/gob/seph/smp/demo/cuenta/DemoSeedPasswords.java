package mx.gob.seph.smp.demo.cuenta;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Las migraciones crean los usuarios sin contraseña; al arrancar se les pone `demo.seed.password` con BCrypt. */
@Component
public class DemoSeedPasswords implements ApplicationRunner {
    private final CuentaRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final String seedPassword;

    public DemoSeedPasswords(
            CuentaRepository repository,
            PasswordEncoder passwordEncoder,
            @Value("${demo.seed.password}") String seedPassword) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.seedPassword = seedPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (long userId : repository.sinPassword()) {
            repository.fijarHash(userId, passwordEncoder.encode(seedPassword));
        }
    }
}
