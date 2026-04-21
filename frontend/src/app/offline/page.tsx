export default function OfflinePage() {
  return (
    <main className="offline-shell">
      <section className="panel offline-panel">
        <span className="brand-chip">Sin conexion</span>
        <h1>La app sigue disponible con datos locales.</h1>
        <p>Cuando la conectividad vuelva, la cola pendiente se enviara al backend y la replica local se actualizara de nuevo.</p>
      </section>
    </main>
  );
}