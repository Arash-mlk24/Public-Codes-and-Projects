package CompleteNumberPackage;

public class CompleteNumber {

	public static void main(String[] args) {
		
		int sum = 0, numbers = 1000;
		
		for (int i = 1; i < numbers; i++) {
			
			for (int j = 1; j < i; j++) {
				
				if ( i % j == 0 ) sum += j;
				
			}
			
			if ( sum == i ) System.out.println(i);
			sum = 0;
			
		}
		
	}

}
