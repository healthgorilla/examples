<?php
$token = 'PUT BEARER TOKEN HERE';

function readCSV() {
    $file = fopen("Patient.csv","r");
    $patientArray = array();
    for ($i=0; $i<9000; $i++) {
        $patientArray[] = fgets($file);
        $patientArray[$i] = str_replace("\n",'',$patientArray[$i]);
        $patientArray[$i] = trim($patientArray[$i]);
        if (empty($patientArray[$i]) ){
            array_pop($patientArray);
            break;
        }
    }
    fclose($file);
    //print_r($patientArray);
    /*echo '<pre>';
    print_r($patientArray);
    echo '</pre>'; exit;*/
    return $patientArray;
}
/**
 * cwEnroll
 * 
 * Enroll patient into CommonWell MPI as new or matching person
 */
function cwEnroll($type,$patientId = null, $cwId = null) {
    global $token;
    $curl = curl_init();
    if ($type == 'new') {
        $url = 'https://api.healthgorilla.com/fhir/Patient/'.$patientId.'/$cw-enroll';
    } elseif ($type == 'match') {
        $url = 'https://api.healthgorilla.com/fhir/Patient/'.$patientId.'/$cw-enroll?person='.$cwId;
    }
    curl_setopt_array($curl, array(
        CURLOPT_URL => $url,
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_ENCODING => '',
        CURLOPT_MAXREDIRS => 10,
        CURLOPT_TIMEOUT => 0,
        CURLOPT_FOLLOWLOCATION => true,
        CURLOPT_HTTP_VERSION => CURL_HTTP_VERSION_1_1,
        CURLOPT_CUSTOMREQUEST => 'GET',
        CURLOPT_HTTPHEADER => array(
            'Content-Type: application/json',
            'Authorization: Bearer '.$token
        ),
    ));

    $response = curl_exec($curl);
    $responseCode = curl_getinfo($curl,CURLINFO_HTTP_CODE);
    curl_close($curl);
    if ($responseCode == 200) {
        file_put_contents('enrollment', 'New Enrollment for   '.$patientId.' with response code '.$responseCode."\n", FILE_APPEND);
        return;
    } else {
        file_put_contents('enrollment', 'Fail on Enrollment for   '.$patientId.' with response code '.$responseCode."\n", FILE_APPEND);
        return;
    }
}


/**
 * cwSearch
 * 
 * search CW for a match
 */
function cwSearch($patientId = null) {
    global $token;
    $curl = curl_init();

    curl_setopt_array($curl, array(
        CURLOPT_URL => 'https://api.healthgorilla.com/fhir/Patient/'.$patientId.'/$cw-search',
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_ENCODING => '',
        CURLOPT_MAXREDIRS => 10,
        CURLOPT_TIMEOUT => 0,
        CURLOPT_FOLLOWLOCATION => true,
        CURLOPT_HTTP_VERSION => CURL_HTTP_VERSION_1_1,
        CURLOPT_CUSTOMREQUEST => 'GET',
        CURLOPT_HTTPHEADER => array(
            'Authorization: Bearer '.$token
        ),
    ));

    $response = curl_exec($curl);
    $responseCode = curl_getinfo($curl,CURLINFO_HTTP_CODE);
    curl_close($curl);
    $json = json_decode($response,true);
    if ($responseCode != 400) {
        if ($json['total'] == 0) {
            file_put_contents('enrollment', 'Enrolling New Patient in CW  '.$patientId."\n", FILE_APPEND);
            //enroll in CW going to be new (new function)
            cwEnroll('new',$patientId);
            return;
        } else {
            foreach ($json['entry'] as $entry) {
                $cwId = $entry['resource']['id'];
                file_put_contents('enrollment', 'Enrolling Matched Patient in CW with patient ID: '.$patientId. ' and CW ID '.$cwId."\n", FILE_APPEND);
                cwEnroll('match',$patientId, $cwId);
                break;
            }
            return;

        }
    }   elseif ($responseCode == 400) {
        file_put_contents('enrollment', 'Enrollment Failed for Bad ZipCode for '.$patientId."\n", FILE_APPEND);
        return;
    }

}

/**
 * cw Lookup
 * Look up to see if they are enrolled in CW already
 */
function cwLookup($patientId = null) {
    global $token;
    
    //lookup to see if they are already enrolled in CW
    $curl = curl_init();
    curl_setopt_array($curl, array(
        CURLOPT_URL => 'https://api.healthgorilla.com/fhir/Patient/'.$patientId.'/$cw-lookup',
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_ENCODING => '',
        CURLOPT_MAXREDIRS => 10,
        CURLOPT_TIMEOUT => 0,
        CURLOPT_FOLLOWLOCATION => true,
        CURLOPT_HTTP_VERSION => CURL_HTTP_VERSION_1_1,
        CURLOPT_CUSTOMREQUEST => 'GET',
        CURLOPT_HTTPHEADER => array(
            'Authorization: Bearer '.$token
        ),
    ));

    $response = curl_exec($curl);
    $responseCode = curl_getinfo($curl,CURLINFO_HTTP_CODE); 
    curl_close($curl);
    if ($responseCode == 200) {
        file_put_contents('enrollment', 'Patient is already enrolled:  '.$patientId."\n", FILE_APPEND);
        return;
    } elseif ($responseCode == 404) {
        //searchCW
        cWSearch($patientId);
    }
    /*echo '<pre>';
    print_r($json);
    echo '</pre>'; exit;*/
}

function asyncSearch($patientID = null) {
    global $token;
    if ($patientID) {
        $headers =[];
        
        $curl = curl_init();

        curl_setopt_array($curl, array(
        CURLOPT_URL => 'https://api.healthgorilla.com/fhir/DocumentReference/$cw-search?patient='.$patientID.'&_format=json',
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_ENCODING => '',
        CURLOPT_MAXREDIRS => 10,
        CURLOPT_HEADER => 1,
        CURLOPT_TIMEOUT => 0,
        CURLOPT_FOLLOWLOCATION => true,
        CURLOPT_HTTP_VERSION => CURL_HTTP_VERSION_1_1,
        CURLOPT_CUSTOMREQUEST => 'GET',
        CURLOPT_HTTPHEADER => array(
            'Authorization: Bearer '.$token,
            'Prefer: respond-async'
        ),
        ));
    
        $response = curl_exec($curl);
        $responseCode = curl_getinfo($curl,CURLINFO_HTTP_CODE); 
        curl_close($curl);
        
        if ($responseCode == 202 || $responseCode == 200) {
            $headers = [];
            $output = rtrim($response);
            $data = explode("\n",$output);
            $headers['status'] = $data[0];
            array_shift($data);

            foreach($data as $part){
                //some headers will contain ":" character (Location for example), and the part after ":" will be lost
                $middle = explode(":",$part,2);
                //Supress warning message if $middle[1] does not exist
                if ( !isset($middle[1]) ) { $middle[1] = null; }
                $headers[trim($middle[0])] = trim($middle[1]);
            }

            $locationHeader = $headers['Location'];
            return $locationHeader;
        } else {
            file_put_contents('errors', 'There was an error on the Async Request for  '.$patientID."\n", FILE_APPEND);
            return false;
        }
    }
}

function getAsyncResults($searchLocation = null, $patientId = null) {
    global $token;
    $requestOK = false;
    do {
        sleep(2);
        $curl = curl_init();

        curl_setopt_array($curl, array(
            CURLOPT_URL => 'https://api.healthgorilla.com'. $searchLocation,
            CURLOPT_RETURNTRANSFER => true,
            CURLOPT_ENCODING => '',
            CURLOPT_MAXREDIRS => 10,
            CURLOPT_TIMEOUT => 0,
            CURLOPT_FOLLOWLOCATION => true,
            CURLOPT_HTTP_VERSION => CURL_HTTP_VERSION_1_1,
            CURLOPT_CUSTOMREQUEST => 'GET',
            CURLOPT_HTTPHEADER => array(
                'Authorization: Bearer '.$token,
                'Content-Type: application/xml'
            ),
        ));

        $response = curl_exec($curl);
        $responseCode = curl_getinfo($curl,CURLINFO_HTTP_CODE); 
        curl_close($curl);
        if ($responseCode == 200) {
            $json = json_decode($response,true);
            return $json;
            $requestOK = true;
        } elseif ($responseCode == 202) {
            echo '202 on ' .$patientId;
        } else {
            echo $responseCode.' on patient '.$patientId;
            file_put_contents('errors', 'No CW Records for  '.$patientId."\n", FILE_APPEND);
            $requestOK = false;
            return false;
        }
    } while ($requestOK == false);
    return;
}

function processUrl($i = null, $patientId = null, $url = null,$organization = null){
    global $token;
    $curl = curl_init();
    //sleep(2);
    curl_setopt_array($curl, array(
        CURLOPT_URL => $url,
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_ENCODING => '',
        CURLOPT_MAXREDIRS => 10,
        CURLOPT_TIMEOUT => 0,
        CURLOPT_FOLLOWLOCATION => true,
        CURLOPT_HTTP_VERSION => CURL_HTTP_VERSION_1_1,
        CURLOPT_CUSTOMREQUEST => 'GET',
        CURLOPT_HTTPHEADER => array(
            'Authorization: Bearer '.$token,
            'Content-Type: application/xml'
        ),
    ));

    $response = curl_exec($curl);
    $organizationSanitized = str_replace('/','_',$organization);
    curl_close($curl);
    file_put_contents($patientId."/CCD_".$organizationSanitized."_".$i,$response);
    sleep(6);

}


function processResults($results = null, $patientId = null) {
    if (!array_key_exists('entry',$results)) {
        return false;
    } 
    //make a directory for the patient
    mkdir($patientId);
    $i = 1;
    foreach ($results['entry'] as $entry) {
        //get the organization
        if (array_key_exists('contained',$entry['resource']) ) {
            foreach ($entry['resource']['contained'] as $possibleOrg) {
                if($possibleOrg['resourceType'] == 'PractitionerRole') {
                    $organization = $possibleOrg['organization']['display'];
                }
            } //end foreach ($entry['resource']['contained'] as $possibleOrg) {
        } else {
            $organization = 'no_organization';
        } //end if (array_key_exists('contained',$entry['resource']) ) {
            //get the binary URL
        foreach ($entry['resource']['content'] as $content) {
            $url = $content['attachment']['url'];
            //don't use sandbox URL in production
            //$sandboxUrl = str_replace('www','sandbox',$url);
            //make the call for the XML data and save it to a file in the patient's folder
            file_put_contents('urls', 'URL for patient  '.$patientId.':  '.$url."\n", FILE_APPEND);
            processUrl($i, $patientId, $url,$organization);
            $i++;
        }
    }// endforeach ($results['entry'] as $entry)
    return true;
    
    
}

function process_Patients() {
    $patients = readCSV();
    //print_r($patients); 
    foreach ($patients as $patient) {
        //CommonWell Lookup
        cwLookup($patient);
        //hit the async request
        $searchLocation = asyncSearch($patient);
        $asyncResults = getAsyncResults($searchLocation, $patient);
        if($asyncResults) {
            if (processResults($asyncResults, $patient) ){
                file_put_contents('success', 'Success for  '.$patient."\n", FILE_APPEND);
            } else {
                file_put_contents('noResults', 'No Results For  '.$patient."\n", FILE_APPEND);
            };
        }
        //echo $searchLocation; exit;
        //hit the DocumentResult for that patient until the code returns success
        
    } //end foreach($patients as $patient)
    echo "<h2>PROCESS COMPLETE</h2>";
}

?>

<form action="" method="post">
           
    <input name="submit" type="submit" value="Grab CCD's">
</form>

<?php
    if (isset($_POST['submit'])) {
       
        process_Patients();
    }
?>