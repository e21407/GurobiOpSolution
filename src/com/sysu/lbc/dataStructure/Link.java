package com.sysu.lbc.dataStructure;

public class Link {
    public Integer srcNodeId;
    public Integer dstNodeId;
    public Double bandwidth;

    public Link(Integer srcNodeId, Integer dstNodeId, Double bandwidth) {
        this.srcNodeId = srcNodeId;
        this.dstNodeId = dstNodeId;
        this.bandwidth = bandwidth;
    }

    public String getLinkKey (){
        return srcNodeId + "_" +  dstNodeId;
    }
    @Override
    public int hashCode() {
        int result = 17;
        result = 31 * result + srcNodeId;
        result = 31 * result + dstNodeId;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Link && ((Link) obj).srcNodeId == this.srcNodeId && ((Link) obj).dstNodeId == dstNodeId;
    }
}
