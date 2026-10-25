package dev.aeltorx.inflow.client;

/** Eight touching square actions per page around a central page selector. */
public record WheelPagination(int page, int pages, int start, int end) {
    public static final int ITEMS = 7;
    public static WheelPagination of(int count, int requestedPage) {
        count=Math.max(0,count);
        int pages=Math.max(1,(count+ITEMS-1)/ITEMS);
        int page=Math.clamp(requestedPage,0,pages-1);
        return new WheelPagination(page,pages,Math.min(count,page*ITEMS),Math.min(count,(page+1)*ITEMS));
    }
}
