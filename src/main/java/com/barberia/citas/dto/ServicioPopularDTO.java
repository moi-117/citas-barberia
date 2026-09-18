package com.barberia.citas.dto;
import com.barberia.citas.dominio.Servicio;

public record ServicioPopularDTO(
        Servicio servicio,
        Long totalReservas
) {}