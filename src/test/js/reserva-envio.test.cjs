const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { webcrypto } = require('node:crypto');

const html = fs.readFileSync(path.join(__dirname, '../../main/resources/static/index.html'), 'utf8');

test('el JavaScript de la página compila', () => {
    for (const [, script] of html.matchAll(/<script\b[^>]*>([\s\S]*?)<\/script>/gi)) {
        new vm.Script(script);
    }
});

test('bloquea doble envío y conserva clave tras perder la respuesta', async () => {
    let enviar;
    let responder;
    const solicitudes = [];
    const elementos = {
        citaForm: { classList: { contains: () => false }, addEventListener: (_, fn) => { enviar = fn; } },
        clienteNombre: { value: 'Cliente Prueba' },
        clienteTelefono: { value: '3105550123' },
        clienteEmail: { value: 'prueba@example.com' },
        btnConfirmarReserva: { disabled: false, textContent: 'Reservar' }
    };
    const estado = { servicio: { id: 1 }, barbero: { id: 'barbero' }, fecha: '2030-01-07', hora: '10:00' };
    const contexto = vm.createContext({
        document: { getElementById: id => elementos[id] }, estado, crypto: webcrypto,
        console: { error() {} }, mostrarMensaje() {}, leerError: async () => 'Conflicto',
        fetchSeguro: async (_, opciones) => {
            solicitudes.push(opciones);
            return new Promise((resolve, reject) => { responder = { resolve, reject }; });
        }
    });
    const inicio = html.indexOf('    let reservaEnCurso = false;');
    const fin = html.indexOf('    function mostrarMensaje', inicio);
    vm.runInContext(html.slice(inicio, fin), contexto);
    const evento = { preventDefault() {} };
    const primero = enviar(evento);
    await enviar(evento);
    assert.equal(solicitudes.length, 1);
    assert.equal(elementos.btnConfirmarReserva.disabled, true);
    responder.reject(new Error('Respuesta perdida'));
    await primero;
    assert.equal(elementos.btnConfirmarReserva.disabled, false);
    const segundo = enviar(evento);
    assert.equal(solicitudes[0].headers['Idempotency-Key'], solicitudes[1].headers['Idempotency-Key']);
    assert.match(solicitudes[0].headers['Idempotency-Key'], /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
    responder.resolve({ ok: false });
    await segundo;
    estado.hora = '12:00';
    const tercero = enviar(evento);
    assert.notEqual(solicitudes[1].headers['Idempotency-Key'], solicitudes[2].headers['Idempotency-Key']);
    responder.resolve({ ok: false });
    await tercero;
});
