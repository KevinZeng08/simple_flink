package main;
import java.util.concurrent.LinkedBlockingDeque;

public class MapJob extends Thread{
	
	Job job = null;
	int mapperId;
	Router router = null;
	
	public MapJob(Job job, int id) {
		this.job = job;
		this.mapperId = id;
	}
	
	public void jobSetup(Router router) {
		this.router = router;
	}
	
	public void run() {
		//job.run();
		while(true) {
			if(router!=null) {
				ObjectWrapper obj = router.getNext(mapperId);
				if(obj.getKey().equals("--CHECKPOINT--")) {
					System.out.println("Mapper checkpointed.");
					router.addToAllMaps(new ObjectWrapper("--CHECKPOINT--", null));
				}else {
					job.map(obj, router);
				}
				//job.map(obj, router);
			}
		}
	}

}
