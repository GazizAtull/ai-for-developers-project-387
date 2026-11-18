type Props = { onBook: () => void };

export default function HomePage({ onBook }: Props) {
  return (
    <section className="home">
      <h2>Запишитесь на звонок за минуту</h2>
      <p>
        Выберите тип встречи, удобный день и свободное время. Регистрация не нужна: достаточно имени и email.
      </p>
      <ul className="home-steps">
        <li>Свободные слоты на 14 дней вперёд, пн-пт, 09:00-18:00 (Asia/Almaty)</li>
        <li>Занятое время не показывается, двойная запись невозможна</li>
        <li>Владелец видит все предстоящие встречи в одном списке</li>
      </ul>
      <button className="primary" onClick={onBook}>
        Записаться
      </button>
    </section>
  );
}
