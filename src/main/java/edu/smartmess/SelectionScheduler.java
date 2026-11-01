package edu.smartmess;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodic catch-up; the service also catches up during dashboard reads and admission. */
@Component
public class SelectionScheduler {
  private final MessService mess;

  public SelectionScheduler(MessService mess) {
    this.mess = mess;
  }

  @Scheduled(fixedDelay = 60000, initialDelay = 1000)
  public void refresh() {
    mess.refreshSelections(mess.now().toLocalDate());
  }
}
