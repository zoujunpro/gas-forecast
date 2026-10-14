const observer = new IntersectionObserver((entries) => {
  entries.forEach((entry) => {
    if (entry.isIntersecting) entry.target.classList.add('visible')
  })
}, { threshold: 0.12 })

document.querySelectorAll('.reveal').forEach((el) => observer.observe(el))

document.querySelectorAll('.tech-card').forEach((card) => {
  card.addEventListener('mouseenter', () => {
    document.querySelectorAll('.tech-card').forEach((item) => item.classList.remove('active'))
    card.classList.add('active')
  })
})
