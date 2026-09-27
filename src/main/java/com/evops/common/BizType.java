package com.evops.common;

/** 请求号台账的业务类型常量。 */
public final class BizType {
    public static final String MACHINE_CREATE = "MACHINE_CREATE";
    public static final String MACHINE_UPDATE = "MACHINE_UPDATE";
    public static final String MACHINE_DISABLE = "MACHINE_DISABLE";
    public static final String WORK_ORDER_CREATE = "WORK_ORDER_CREATE";
    public static final String WORK_ORDER_TRANSIT = "WORK_ORDER_TRANSIT";
    public static final String WORK_ORDER_TRACK_AREA = "WORK_ORDER_TRACK_AREA";
    public static final String WORK_ORDER_MANUAL_AREA = "WORK_ORDER_MANUAL_AREA";
    public static final String WORK_ORDER_REVERSE = "WORK_ORDER_REVERSE";
    public static final String SAT_BILL_IMPORT = "SAT_BILL_IMPORT";
    public static final String RECON_WRITEOFF = "RECON_WRITEOFF";

    private BizType() {
    }
}
