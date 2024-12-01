package at.lucny.p2pbackup.shell;

import at.lucny.p2pbackup.application.service.VerificationAgent;
import at.lucny.p2pbackup.verification.service.VerificationService;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;

import jakarta.validation.constraints.NotNull;

@ShellComponent
public class VerificationCommands {

    private final VerificationService verificationService;

    private final VerificationAgent verificationAgent;

    public VerificationCommands(VerificationService verificationService, VerificationAgent verificationAgent) {
        this.verificationService = verificationService;
        this.verificationAgent = verificationAgent;
    }

    @ShellMethod("Triggers the verification for all blocks of the given user")
    public void triggerVerification(@NotNull String userId) {
        this.verificationService.markLocationsForVerification(userId);
        this.verificationAgent.verify();
    }


    @ShellMethod(value = "verifies the necessary remote blocks")
    public void verifyBlocks() {
        this.verificationAgent.verify();
    }
}
