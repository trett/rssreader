import Vue from "vue";
import Component from "vue-class-component";

@Component({
    template: `
    <v-app>
        <v-main>
            <v-container fluid fill-height>
                <v-layout align-center justify-center>
                        <div class="text-xs-center">
                            <v-btn
                                href="/oauth2/authorization/google"
                                color="primary"
                                elevation="7"
                                outlined
                                rounded
                                text
                                x-large
                                dark
                            >
                                <v-icon dark left>fab fa-google</v-icon>Sign up using Google
                            </v-btn>
                        </div>
                </v-layout>
            </v-container>
        </v-main>
    </v-app>
    `,
})
export default class LoginComponent extends Vue {}
