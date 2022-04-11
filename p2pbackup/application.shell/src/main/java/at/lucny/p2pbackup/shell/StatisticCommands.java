package at.lucny.p2pbackup.shell;

import at.lucny.p2pbackup.upload.service.DistributionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;

import java.util.Map;

@ShellComponent
public class StatisticCommands {

    private static final Logger LOGGER = LoggerFactory.getLogger(StatisticCommands.class);

    private final DistributionService distributionService;

    public StatisticCommands(DistributionService distributionService) {
        this.distributionService = distributionService;
    }

    @ShellMethod("prints a statistic about all replicas")
    public void printReplicaStatistic() {
        Map<Integer, Long> statistics = this.distributionService.getNumberOfReplicasStatistic();
        StringBuilder sb = new StringBuilder("\nStatistic about total replicas\n");
        sb.append("------------------------\n");
        for (Map.Entry<Integer, Long> entry : statistics.entrySet()) {
            sb.append(entry.getKey()).append(" replicas: ").append(entry.getValue()).append(" blocks \n");
        }
        sb.append("------------------------\n");

        statistics = this.distributionService.getNumberOfVerifiedReplicasStatistic();
        sb.append("Statistic about verified replicas\n");
        sb.append("------------------------\n");
        for (Map.Entry<Integer, Long> entry : statistics.entrySet()) {
            sb.append(entry.getKey()).append(" verified replicas: ").append(entry.getValue()).append(" blocks \n");
        }
        LOGGER.info(sb.toString());
    }
}
