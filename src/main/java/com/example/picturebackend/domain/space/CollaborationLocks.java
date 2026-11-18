package com.example.picturebackend.domain.space;
import com.example.picturebackend.exception.*;
import org.springframework.stereotype.Service;
import java.util.*;

/** One-process lease manager. A lease expires unless renewed by an active editor. */
@Service
public class CollaborationLocks {
    private final Map<Long,long[]> locks=new HashMap<>();
    public synchronized boolean acquire(Long picture,Long user){
        long now=System.currentTimeMillis();locks.entrySet().removeIf(e->e.getValue()[1]<now);
        long[] lock=locks.get(picture);if(lock!=null && lock[0]!=user)return false;
        locks.put(picture,new long[]{user,now+30000});return true;
    }
    public synchronized void check(Long picture,Long user){
        long[] lock=locks.get(picture);ThrowUtils.throwIf(lock!=null && lock[1]>System.currentTimeMillis() && lock[0]!=user,ErrorCode.OPERATION_ERROR,"另一位成员正在编辑，请稍后重试");
    }
    public synchronized void release(Long picture,Long user){long[] lock=locks.get(picture);if(lock!=null && lock[0]==user)locks.remove(picture);}
}
