package com.ktpm.common.infrastructure;

import com.ktpm.common.UnitOfWork;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class SpringUnitOfWork implements UnitOfWork {
  private final TransactionTemplate reads, writes, mandatory;

  public SpringUnitOfWork(PlatformTransactionManager manager) {
    reads = new TransactionTemplate(manager);
    reads.setReadOnly(true);
    writes = new TransactionTemplate(manager);
    mandatory = new TransactionTemplate(manager);
    mandatory.setPropagationBehavior(TransactionDefinition.PROPAGATION_MANDATORY);
  }

  public <T> T read(Supplier<T> action) {
    return reads.execute(s -> action.get());
  }

  public <T> T write(Supplier<T> action) {
    return writes.execute(s -> action.get());
  }

  public void mandatory(Runnable action) {
    mandatory.executeWithoutResult(s -> action.run());
  }
}
