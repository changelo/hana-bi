package com.anur.core.elect.vote.model;

import java.util.Objects;
/**
 * Created by Anur IjuoKaruKas on 2019/1/22
 *
 * 选票相关
 */
public class Votes {

    /**
     * 该选票的世代信息
     */
    private int generation;

    /**
     * 投递该选票的服务名
     */
    private String serverName;

    public Votes() {
    }

    public Votes(int generation, String serverName) {
        this.generation = generation;
        this.serverName = serverName;
    }

    public int getGeneration() {
        return generation;
    }

    public String getServerName() {
        return serverName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Votes votes = (Votes) o;
        return generation == votes.generation &&
            Objects.equals(serverName, votes.serverName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(generation, serverName);
    }
}
