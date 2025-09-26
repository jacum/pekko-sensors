package nl.pragmasoft.pekko.persistence.inmemory;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Status;
import org.apache.pekko.testkit.TestProbe;

import nl.pragmasoft.pekko.persistence.inmemory.extension.InMemoryJournalStorage;
import nl.pragmasoft.pekko.persistence.inmemory.extension.InMemorySnapshotStorage;
import nl.pragmasoft.pekko.persistence.inmemory.extension.StorageExtension;
import nl.pragmasoft.pekko.persistence.inmemory.extension.StorageExtensionProvider;

import org.junit.Test;


public class StorageExtensionCompileOnlyTest {

    @Test
    public void shouldHaveANiceJavaAPI() {
        ActorSystem actorSystem = ActorSystem.create();
        TestProbe tp = new TestProbe(actorSystem);
        StorageExtension extension = StorageExtensionProvider.get(actorSystem);

        InMemoryJournalStorage.ClearJournal clearJournal = InMemoryJournalStorage.clearJournal();
        ActorRef actorRef = extension.journalStorage(actorSystem.settings().config());
        tp.send(actorRef, clearJournal);
        tp.expectMsg(new Status.Success(""));

        InMemorySnapshotStorage.ClearSnapshots clearSnapshots = InMemorySnapshotStorage.clearSnapshots();
        tp.send(actorRef, clearSnapshots);
        tp.expectMsg(new Status.Success(""));
    }
}
