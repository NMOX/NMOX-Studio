// Vanilla JavaScript Application
document.addEventListener('DOMContentLoaded', function() {
  console.log('App loaded!');
  
  // Add your JavaScript code here
  const app = document.getElementById('app');
  
  // Example: Add a button
  const button = document.createElement('button');
  button.textContent = 'Click me!';
  button.addEventListener('click', function() {
    alert('Hello from Vanilla JS!');
  });
  
  app.appendChild(button);
});
