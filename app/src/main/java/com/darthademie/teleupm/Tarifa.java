package com.darthademie.teleupm;

public class Tarifa {
    public String id;
    public String nombre;
    public String descripcion;
    public String userPushId;

    public Tarifa() {}

    public Tarifa(String id, String nombre, String descripcion) {
        this.id = id;
        this.nombre = nombre;
        this.descripcion = descripcion;
    }
}
