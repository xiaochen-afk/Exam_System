Component({
  properties: { state: String, message: String, requestId: String },
  methods: { retry() { this.triggerEvent('retry') } }
})
