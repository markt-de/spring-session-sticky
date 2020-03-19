# Sticky sessions for spring-session

The [spring-session](https://github.com/spring-projects/spring-session/) project
has an [open issue](https://github.com/spring-projects/spring-session/issues/6) about supporting the concept
of sticky sessions, where a load-balancer is configured to
send one client always to the same server if possible.

This allows some optimizations like keeping a local cache of sessions, and
replicating changes to the centralized session store asynchronously.

This repository aims to provide a reference implementation of this feature,
which can ultimately be merged into spring-session upstream.

## Configuring StickySessionRepository

This project provides a `StickySessionRepository` class, which manages a local
cache of sessions and delegates to another repository for centralized session
management (e.g. `RedisIndexedSessionRepository`).

Configuration could look something like this:

```java

@Configuration
public class StickySessionConfig extends RedisHttpSessionConfiguration {

  private ApplicationEventPublisher eventPublisher;

  @Override
  @Autowired
  public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
    this.eventPublisher = applicationEventPublisher;
    super.setApplicationEventPublisher(applicationEventPublisher);
  }

  @Bean
  public StickySessionRepository<?> stickySessionRepository(@Autowired RedisSessionRepository sessionRepository) {
    var adapter = new StickyRedisSessionRepositoryAdapter(sessionRepository);
    // would make the cache in a bean, but Redis repo hides the RedisSession type *shrug*
    var repository = StickySessionRepository(adapter, new ConcurrentHashMap(256, 0.75F, 16));
    repository.setApplicationEventPublisher(eventPublisher);
    repository.setAsyncSaveExecutor(Executors.newFixedThreadPool(16));
    repository.setRevalidateAfter(Duration.ofSeconds(30));
    return repository;
  }

  @Override
  @Bean
  // override in order to qualify the stickySessionRepository
  public <S extends Session> SessionRepositoryFilter<? extends Session> springSessionRepositoryFilter(
      @Qualifier("stickySessionRepository") SessionRepository<S> sessionRepository) {
    return super.springSessionRepositoryFilter(sessionRepository);
  }
}

```
