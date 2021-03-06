process.env.UV_THREADPOOL_SIZE = 128;

const csv = require('csv-parser');
const fs = require('fs');
const createCsvWriter = require('csv-writer').createObjectCsvWriter;
const axios = require('axios');
const results = [];

const BASE_URL = 'https://sandbox.healthgorilla.com/fhir/';

const httpClient = axios.create({
    baseURL: BASE_URL,
    headers: {
        Authorization: 'Bearer ',
        'Content-Type': 'application/json'
    },
});

httpClient.interceptors.request.use((config) => {
    config.headers['Authorization'] = 'Bearer ';
    return config;
});

const csvWriter = createCsvWriter({
    path: './report.csv',
    header: [
        { id: 'patient', title: 'PATIENT' },
        { id: 'count', title: 'COUNT' },
    ],
});

const records = [];
fs.createReadStream('report.csv')
    .pipe(csv())
    .on('data', async (row) => {
        console.log('csv data +++', row);
        let lastName = row['First Name'];
        try {
            const fName = row['First Name'];
            const lName = row['Last Name'];
            const gender = row['Gender'].toLowerCase();
            const dob = new Date(row['DOB']).toISOString().split('T')[0];
            const street = row['Address'];
            const city = row['City'];
            const state = row['State'];
            const zip = row['Zip'];
            const phone = row['Phone'];

            let pId = '';
            let locationUrl = '';

            if (!gender) {
                console.log(
                    `Skipping ${fName} ${lName} because of missing gender field`
                );
                return;
            }

            console.log('patient api call before +++');

            const data = {
                name: { given: fName, family: lName },
                birthDate: dob,
                gender,
                address: {
                    line: [street],
                    city,
                    state,
                    postalCode: zip,
                },
                telecom: {
                    system: 'phone',
                    value: phone,
                },
                resourceType: 'Patient',
            };

            const config = {
                method: 'post',
                url: 'https://sandbox.healthgorilla.com/fhir/Patient',
                headers: {
                    Authorization: 'Bearer ',
                    'Content-Type': 'application/json',
                },
                data: data,
            };

            axios(config)
                .then(function (response) {
                    console.log('resonse +++', response.status);
                    console.log(JSON.stringify(response.data));
                    if (response.status == 201) {
                        console.log('csv add row success case +++++++');
                        var configGetCsv = {
                            method: 'get',
                            url: 'https://sandbox.healthgorilla.com/fhir/Patient',
                            headers: {
                                Authorization: 'Bearer ',
                                'Content-Type': 'application/json',
                            },
                        };
                        axios(configGetCsv)
                            .then(function (response) {
                                const data = response.data.entry;
                                console.log('res data len +++', Object.keys(data).length);
                                for (let index = 0; index < Object.keys(data).length; index++) {
                                    const element = data[index];
                                    const str = element.resource.name[0].given || '';
                                    if (lastName == str) {
                                        console.log('ele name +++', str);
                                        pId = element.resource.id;
                                        console.log('pId value +++', pId);
                                    }
                                }

                                const configSearchPid = {
                                    method: 'get',
                                    url: `https://sandbox.healthgorilla.com/fhir/Patient/${pId}/$cw-search?_format=json`,
                                    headers: {
                                        Authorization: 'Bearer '
                                    },
                                };

                                axios(configSearchPid)
                                    .then(function (response) {
                                        console.log(
                                            'search Pid res +++',
                                            JSON.stringify(response.data)
                                        );
                                        if (response.data.total !== 0) {
                                            // await httpClient.get(`/Patient/${PatientID}/$cw-enroll`)
                                            console.log('search Pid total +++', response.data.total);
                                            const P360SearchResConfig = {
                                                method: 'get',
                                                url: `https://sandbox.healthgorilla.com/fhir/DocumentReference/$p360-search?patient=${pId}&_format=json`,
                                                headers: {
                                                    Authorization: 'Bearer ',
                                                    Prefer: 'respond-async'
                                                },
                                            };

                                            axios(P360SearchResConfig)
                                                .then(function (response) {
                                                    console.log('P360SearchRes Status +++++++', response.status);
                                                    locationUrl = response.headers.location;
                                                    console.log('locationUrl +++++++', locationUrl);
                                                })
                                                .catch(function (error) {
                                                    console.log(error);
                                                });

                                            const P360EntriesConfig = {
                                                method: 'get',
                                                url: `https://sandbox.healthgorilla.com${locationUrl}?_format=json`,
                                                headers: {
                                                    Authorization: 'Bearer ',
                                                    Prefer: 'respond-async'
                                                },
                                            };

                                            axios(P360EntriesConfig)
                                            .then(function (response) {
                                                console.log(
                                                    'P360Entries Response data +++++++',
                                                    JSON.stringify(response.body)
                                                );
                                            })
                                            .catch(function (error) {
                                                console.log('P360Entries api call error +++++++', error.status);
                                            });
                                        }
                                    })
                                    .catch(function (error) {
                                        console.log(error);
                                    });
                            })
                            .catch(function (error) {
                                console.log('get csv data error case +++', error);
                            });
                    }
                })
                .catch(function (error) {
                    console.log('csv add row errror case -------', error);
                });
        } catch (err) {
            // console.log(`Error for: ${JSON.stringify(row)}`, err);
            console.log(`Error for +++`, err);
        }
    })
    .on('close', async () => {
        console.log(results);
    });