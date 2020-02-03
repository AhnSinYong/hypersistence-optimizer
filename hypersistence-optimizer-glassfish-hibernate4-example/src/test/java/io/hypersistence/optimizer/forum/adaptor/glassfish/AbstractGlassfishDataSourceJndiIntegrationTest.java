package io.hypersistence.optimizer.forum.adaptor.glassfish;

import io.hypersistence.optimizer.HypersistenceOptimizer;
import io.hypersistence.optimizer.core.config.JpaConfig;
import io.hypersistence.optimizer.core.event.Event;
import io.hypersistence.optimizer.forum.domain.Post;
import io.hypersistence.optimizer.hibernate.event.configuration.schema.SchemaGenerationEvent;
import io.hypersistence.optimizer.hibernate.event.mapping.association.Association6Event;
import io.hypersistence.optimizer.hibernate.event.mapping.association.Association7Event;
import io.hypersistence.optimizer.hibernate.event.mapping.association.Association8Event;
import io.hypersistence.optimizer.hibernate.event.mapping.association.fetching.Fetching2Event;
import io.hypersistence.optimizer.hibernate.event.query.QueryEvent;
import io.hypersistence.optimizer.hibernate.event.session.SessionEvent;
import org.jboss.arquillian.junit.Arquillian;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.transaction.SystemException;
import javax.transaction.UserTransaction;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

@RunWith(Arquillian.class)
public abstract class AbstractGlassfishDataSourceJndiIntegrationTest {

    private HypersistenceOptimizer hypersistenceOptimizer;

    @Inject
    private UserTransaction userTransaction;

    @Before
    public void init() {
        hypersistenceOptimizer = new HypersistenceOptimizer(
            new JpaConfig(
                getEntityManagerFactory()
            )
        );
    }

    @Test
    public void test() throws Exception {
        assertEventTriggered(2, Fetching2Event.class);
        assertEventTriggered(1, Association6Event.class);
        assertEventTriggered(1, Association7Event.class);
        assertEventTriggered(1, Association8Event.class);
        assertEventTriggered(1, SchemaGenerationEvent.class);

        doInTransaction(() -> {
            Post post = new Post();
            post.setTitle("High-Performance Java Persistence");
            getEntityManager().persist(post);
        });

        hypersistenceOptimizer.getEvents().clear();

        doInTransaction(() -> {
            assertEventTriggered(0, QueryEvent.class);

            List<Post> posts = getEntityManager().createQuery(
                "select p " +
                "from Post p ", Post.class)
            .setMaxResults(5)
            .getResultList();

            assertEquals(1, posts.size());

            assertEventTriggered(1, QueryEvent.class);

            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                Thread.interrupted();
            }
        });

        assertEventTriggered(1, SessionEvent.class);
    }

    private void doInTransaction(VoidCallable callable) {
        try {
            userTransaction.begin();
            getEntityManager().joinTransaction();
            callable.call();
            userTransaction.commit();
        } catch (Exception e) {
            try {
                userTransaction.rollback();
            } catch (SystemException e1) {
                throw new IllegalStateException(e);
            }
            throw new IllegalStateException(e);
        }
    }

    public interface VoidCallable {
        void call();
    }

    protected void assertEventTriggered(int expectedCount, Class<? extends Event> eventClass) {
        int count = 0;

        for (Event event : hypersistenceOptimizer.getEvents()) {
            if (event.getClass().equals(eventClass)) {
                count++;
            }
        }

        assertSame(expectedCount, count);
    }

    protected abstract EntityManager getEntityManager();

    protected abstract EntityManagerFactory getEntityManagerFactory();
}
