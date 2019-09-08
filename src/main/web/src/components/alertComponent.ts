import Vue from "vue";
import Component from "vue-class-component";
import EventBus from "../eventBus";

@Component({
    template: `
        <v-alert v-model="alert"
                 dismissible
                 dense
                 colored-border
                 :type="alertType"
                 :color="alertType"
                 border="left"
                 elevation="2"
                 @input="close()">
            {{ message }}
        </v-alert>
    `,
})
export default class AlertComponent extends Vue {

    private alert = false;

    private alertType = "";

    private message = "";

    public mounted(): void {
        EventBus.$on("error", (message: string) => {
            this.alert = true;
            this.alertType = "error";
            this.message = message;
        });
    }

    private close(): void {
        this.alert = false;
    }
}
