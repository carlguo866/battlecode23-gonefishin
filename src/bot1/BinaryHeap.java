package bot1;

public class BinaryHeap {
     
    private static final int d= 2;
    private int[] heap;
    private int heapSize;
     
    /**
     * This will initialize our heap with default size. 
     */
    public BinaryHeap(int capacity){
        heapSize = 0;
        heap = new int[ capacity+1];
    }
     
    public boolean isEmpty(){
        return heapSize==0;
    }

    public boolean isFull(){
        return heapSize == heap.length;
    }
     
    private int parent(int i){
        return (i-1)/d;
    }
     
    private int kthChild(int i,int k){
        return d*i+k;
    }

    public void insert(int x){
        heap[heapSize++] = x;
        heapifyUp(heapSize-1);
    }

    public int delete(int x){
        int key = heap[x];
        heap[x] = heap[heapSize -1];
        heapSize--;
        heapifyDown(x);
        return key;
    }
 
    private void heapifyUp(int i) {
        int temp = heap[i];
        while(i>0 && temp < heap[parent(i)]){
            heap[i] = heap[parent(i)];
            i = parent(i);
        }
        heap[i] = temp;
    }
     
    private void heapifyDown(int i){
        int child;
        int temp = heap[i];
        while(kthChild(i, 1) < heapSize){
            child = minChild(i);
            if(temp > heap[child]){ 
                heap[i] = heap[child]; 
            }
            else break; 
            i = child; 
        } 
        heap[i] = temp; 
    } 

    private int minChild(int i) { 
        int leftChild = kthChild(i, 1); 
        int rightChild = kthChild(i, 2); 
        return heap[leftChild]<heap[rightChild]?leftChild:rightChild;
    }

    public int findMin(){
        return heap[0];
    }
}
