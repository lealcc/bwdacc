package com.leal.comfyremote;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/** Serializes file mutations against a backup snapshot or a restore commit. */
final class VaultMaintenance {
    private static final ReentrantReadWriteLock LOCK=new ReentrantReadWriteLock(true);
    private static volatile boolean restartRequired;
    static void beginWrite()throws IOException{
        LOCK.readLock().lock();
        if(restartRequired){LOCK.readLock().unlock();throw new IOException(UiLanguage.t("Reabra o app para concluir a restauração."));}
    }
    static void endWrite(){LOCK.readLock().unlock();}
    static AutoCloseable exclusive()throws Exception{
        if(!LOCK.writeLock().tryLock(30,TimeUnit.SECONDS))throw new IOException(UiLanguage.t("Arquivos em uso. Tente novamente após concluir a operação."));
        if(restartRequired){LOCK.writeLock().unlock();throw new IOException(UiLanguage.t("Reabra o app para concluir a restauração."));}
        return ()->LOCK.writeLock().unlock();
    }
    static void requireRestart(){restartRequired=true;}
    static boolean restartRequired(){return restartRequired;}
}
