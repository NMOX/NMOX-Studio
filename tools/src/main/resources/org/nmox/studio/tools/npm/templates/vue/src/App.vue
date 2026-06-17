<template>
  <div id="app">
    <h1>{{ msg }}</h1>
    <p>Edit src/App.vue and save to reload.</p>
  </div>
</template>

<script>
export default {
  name: 'App',
  data() {
    return {
      msg: 'Welcome to Vue'
    }
  }
}
</script>

<style>
#app {
  font-family: Avenir, Helvetica, Arial, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  text-align: center;
  color: #2c3e50;
  margin-top: 60px;
}
</style>
